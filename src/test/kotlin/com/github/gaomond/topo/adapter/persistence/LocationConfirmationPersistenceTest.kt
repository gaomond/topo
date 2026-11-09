package com.github.gaomond.topo.adapter.persistence

import com.github.gaomond.topo.adapter.persistence.jpa.GameJpaRepository
import com.github.gaomond.topo.adapter.persistence.jpa.PlayerJpaRepository
import com.github.gaomond.topo.config.ClockConfig
import com.github.gaomond.topo.domain.exception.PlayerAlreadyConfirmedException
import com.github.gaomond.topo.domain.model.AreaPreset
import com.github.gaomond.topo.domain.model.ConfirmedLocation
import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.model.Game
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.ObjectType
import com.github.gaomond.topo.domain.model.Player
import com.github.gaomond.topo.support.PostgisTestContainer
import com.github.gaomond.topo.usecase.ConfirmLocationInput
import com.github.gaomond.topo.usecase.ConfirmLocationUseCase
import com.github.gaomond.topo.usecase.ConfirmedLocationResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.dao.InvalidDataAccessApiUsageException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ConfirmLocationUseCase::class, ClockConfig::class, PostgisHullService::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LocationConfirmationPersistenceTest {
    companion object {
        @JvmStatic @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            val c = PostgisTestContainer.instance
            registry.add("spring.datasource.url") { c.jdbcUrl }
            registry.add("spring.datasource.username") { c.username }
            registry.add("spring.datasource.password") { c.password }
        }
    }

    @Autowired private lateinit var gameRepository: GameJpaRepository

    @Autowired private lateinit var playerRepository: PlayerJpaRepository

    @Autowired private lateinit var confirmLocationUseCase: ConfirmLocationUseCase

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired private lateinit var transactions: PlatformTransactionManager
    private val at = Instant.parse("2026-11-08T01:02:03Z")
    private val location = ConfirmedLocation(Coordinate(35.68, 139.76), at)

    private fun seed(): Pair<UUID, UUID> {
        val gameId = UUID.randomUUID()
        val playerId = UUID.randomUUID()
        TransactionTemplate(transactions).executeWithoutResult {
            gameRepository.createWithoutCreator(Game.create(gameId, playerId, 3, ObjectType.SHRINE, AreaPreset.ALL[1]))
            gameRepository.updateStatus(Game.restore(gameId, GameStatus.ACTIVE, 3, null, ObjectType.SHRINE, 2_000_000.0))
            playerRepository.createPlayer(Player.create(playerId, gameId, "確認者"))
            playerRepository.flush()
        }
        return gameId to playerId
    }

    private fun withGameLock(
        gameId: UUID,
        operation: () -> Unit,
    ) {
        TransactionTemplate(transactions).executeWithoutResult {
            assertNotNull(gameRepository.findByIdWithLock(gameId))
            operation()
        }
    }

    @Test fun test_confirmLocation_withMember_savesPointTimeAndKeepsLiveAndGame() {
        val (gameId, playerId) = seed()
        playerRepository.updateLiveLocation(gameId, playerId, Coordinate(36.0, 140.0), at.minusSeconds(2))
        withGameLock(gameId) { playerRepository.confirmLocation(gameId, playerId, location) }
        assertEquals(location, playerRepository.findByGameIdAndPlayerId(gameId, playerId)?.confirmedLocation)
        val row =
            jdbc.queryForMap(
                "select ST_X(fixed_geom) as x, ST_Y(fixed_geom) as y, ST_SRID(fixed_geom) as srid, GeometryType(fixed_geom) as kind, live_lat, live_lng from player where id = ?",
                playerId,
            )
        assertEquals(139.76, row["x"])
        assertEquals(35.68, row["y"])
        assertEquals(4326, row["srid"])
        assertEquals("POINT", row["kind"])
        assertEquals(36.0, row["live_lat"])
        assertEquals(140.0, row["live_lng"])
        assertEquals(GameStatus.ACTIVE, gameRepository.findSummary(gameId)?.status)
        val results = jdbc.queryForMap("select polygon_geom, area_sqm, area_valid, object_count from game where id = ?", gameId)
        results.values.forEach { assertNull(it) }
    }

    @Test fun test_confirmLocation_withMissingOrNonMember_throwsInternalErrorWithoutWrite() {
        val (gameId, playerId) = seed()
        assertNull(playerRepository.findByGameIdAndPlayerId(UUID.randomUUID(), playerId))
        val wrongGame =
            assertThrows<InvalidDataAccessApiUsageException> {
                withGameLock(gameId) { playerRepository.confirmLocation(UUID.randomUUID(), playerId, location) }
            }
        assertIs<IllegalStateException>(wrongGame.cause)
        val missingPlayer =
            assertThrows<InvalidDataAccessApiUsageException> {
                withGameLock(gameId) { playerRepository.confirmLocation(gameId, UUID.randomUUID(), location) }
            }
        assertIs<IllegalStateException>(missingPlayer.cause)
        assertNull(playerRepository.findByGameIdAndPlayerId(gameId, playerId)?.confirmedLocation)
    }

    @Test fun test_confirmLocation_withoutTransaction_rejectsSave() {
        val (gameId, playerId) = seed()
        assertThrows<IllegalTransactionStateException> { playerRepository.confirmLocation(gameId, playerId, location) }
        assertNull(playerRepository.findByGameIdAndPlayerId(gameId, playerId)?.confirmedLocation)
    }

    @Test fun test_confirm_withSavedPoint_rejectsInDomainWithoutOverwrite() {
        val (gameId, playerId) = seed()
        confirmLocationUseCase.confirm(ConfirmLocationInput(gameId, playerId, location.coordinate))
        val original = playerRepository.findByGameIdAndPlayerId(gameId, playerId)?.confirmedLocation
        assertNotNull(original)
        assertThrows<PlayerAlreadyConfirmedException> {
            confirmLocationUseCase.confirm(ConfirmLocationInput(gameId, playerId, Coordinate(1.0, 2.0)))
        }
        assertEquals(original, playerRepository.findByGameIdAndPlayerId(gameId, playerId)?.confirmedLocation)
    }

    @Test fun test_confirm_withConcurrentRequests_serializesAndRejectsSecondInDomain() {
        val (gameId, playerId) = seed()
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val alternatives = listOf(location.coordinate, Coordinate(36.0, 140.0))
        try {
            val requests =
                alternatives.map { point ->
                    executor.submit<Result<ConfirmedLocationResult>> {
                        ready.countDown()
                        check(start.await(5, TimeUnit.SECONDS))
                        runCatching { confirmLocationUseCase.confirm(ConfirmLocationInput(gameId, playerId, point)) }
                    }
                }
            check(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            val results = requests.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it.isSuccess })
            assertIs<PlayerAlreadyConfirmedException>(results.single { it.isFailure }.exceptionOrNull())
            val winner = results.indexOfFirst { it.isSuccess }
            assertEquals(
                ConfirmedLocation(alternatives[winner], results[winner].getOrThrow().confirmedAt),
                playerRepository.findByGameIdAndPlayerId(gameId, playerId)?.confirmedLocation,
            )
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }
}
