package com.github.gaomond.topo.adapter.persistence

import com.github.gaomond.topo.adapter.persistence.jpa.GameJpaRepository
import com.github.gaomond.topo.adapter.persistence.jpa.PlayerJpaRepository
import com.github.gaomond.topo.domain.model.AreaPreset
import com.github.gaomond.topo.domain.model.ConfirmedLocation
import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.model.Game
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.ObjectType
import com.github.gaomond.topo.domain.model.Player
import com.github.gaomond.topo.support.PostgisTestContainer
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ゲームとプレイヤーの一致条件付き更新、所属不一致時の未更新、記録時刻の反映を検証する。 */
@DataJpaTest(properties = ["spring.jpa.properties.hibernate.generate_statistics=true"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LiveLocationPersistenceTest {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            val c = PostgisTestContainer.instance
            registry.add("spring.datasource.url") { c.jdbcUrl }
            registry.add("spring.datasource.username") { c.username }
            registry.add("spring.datasource.password") { c.password }
        }
    }

    @Autowired
    private lateinit var gameRepository: GameJpaRepository

    @Autowired
    private lateinit var playerRepository: PlayerJpaRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    // JPQL UPDATE（@Modifying）は 1 次キャッシュを同期しないため、読み戻しは jdbcTemplate で DB を直接見る。
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private fun seedGame(status: GameStatus = GameStatus.ACTIVE): UUID {
        val gameId = UUID.randomUUID()
        gameRepository.createWithoutCreator(Game.create(gameId, UUID.randomUUID(), 3, ObjectType.SHRINE, AreaPreset.ALL[1]))
        gameRepository.updateStatus(Game.restore(gameId, status, 99, null, ObjectType.TEMPLE, 123.5))
        gameRepository.flush()
        return gameId
    }

    private fun seedPlayer(gameId: UUID): UUID {
        val playerId = UUID.randomUUID()
        playerRepository.createPlayer(Player.create(playerId, gameId, "プレイヤー"))
        playerRepository.flush()
        return playerId
    }

    @Test
    fun test_updateLiveLocation_withMatchingPlayer_updatesLiveColumns() {
        val gameId = seedGame()
        val playerId = seedPlayer(gameId)
        val at = Instant.parse("2026-07-04T12:00:00Z")

        val updated =
            playerRepository.updateLiveLocation(gameId, playerId, Coordinate(35.68, 139.76), at)
        entityManager.clear()

        assertTrue(updated)
        val row =
            jdbcTemplate.queryForMap(
                "SELECT live_lat, live_lng, live_at FROM player WHERE id = ?",
                playerId,
            )
        assertEquals(35.68, row["live_lat"])
        assertEquals(139.76, row["live_lng"])
    }

    @Test
    fun test_updateLiveLocation_withMismatchedGameId_updatesZeroRows() {
        val gameId = seedGame()
        val otherGameId = seedGame()
        val playerId = seedPlayer(gameId)

        val updated =
            playerRepository.updateLiveLocation(otherGameId, playerId, Coordinate(1.0, 2.0), Instant.now())
        entityManager.clear()

        assertFalse(updated)
        val row =
            jdbcTemplate.queryForMap(
                "SELECT live_lat, live_lng FROM player WHERE id = ?",
                playerId,
            )
        assertNull(row["live_lat"])
        assertNull(row["live_lng"])
    }

    @Test
    fun test_updateLiveLocation_withUnknownPlayer_updatesZeroRows() {
        val gameId = seedGame()
        val updated =
            playerRepository.updateLiveLocation(gameId, UUID.randomUUID(), Coordinate(1.0, 2.0), Instant.now())
        assertFalse(updated)
    }

    @Test
    fun `test_findByGameId_live送信済み_LiveLocationを射影`() {
        val gameId = seedGame()
        val playerId = seedPlayer(gameId)
        val at = Instant.parse("2026-07-04T12:00:00Z")
        playerRepository.updateLiveLocation(gameId, playerId, Coordinate(35.68, 139.76), at)
        entityManager.clear()

        val reading = playerRepository.findByGameId(gameId).single()

        assertEquals(35.68, reading.live?.coordinate?.lat)
        assertEquals(139.76, reading.live?.coordinate?.lng)
        assertEquals(at, reading.live?.at)
    }

    @Test
    fun `test_findByGameId_live未送信_liveはnull`() {
        val gameId = seedGame()
        seedPlayer(gameId)
        entityManager.clear()

        val reading = playerRepository.findByGameId(gameId).single()

        assertNull(reading.live)
    }

    @Test
    fun test_updateLiveLocation_setsLiveAtToGivenInstant() {
        val gameId = seedGame()
        val playerId = seedPlayer(gameId)
        val at = Instant.parse("2026-07-04T09:30:00Z")

        playerRepository.updateLiveLocation(gameId, playerId, Coordinate(10.0, 20.0), at)
        entityManager.clear()

        val storedAt =
            jdbcTemplate.queryForObject(
                "SELECT live_at FROM player WHERE id = ?",
                java.sql.Timestamp::class.java,
                playerId,
            )
        assertEquals(at, storedAt?.toInstant())
    }

    @Test
    fun test_findByGameId_multipleSavedPoints_preservesOrderAndFixedValueAfterLiveUpdate() {
        val gameId = seedGame()
        val ids = List(3) { seedPlayer(gameId) }
        val at = Instant.parse("2026-11-09T12:00:00Z")
        ids.forEachIndexed { index, id ->
            jdbcTemplate.update("update player set joined_at = ? where id = ?", java.sql.Timestamp.from(at.plusSeconds(index.toLong())), id)
        }
        val fixed = ConfirmedLocation(Coordinate(35.59, 139.60), at)
        playerRepository.confirmLocation(gameId, ids[0], fixed)
        playerRepository.confirmLocation(gameId, ids[1], ConfirmedLocation(Coordinate(35.591, 139.601), at.plusSeconds(1)))
        playerRepository.updateLiveLocation(gameId, ids[0], Coordinate(36.0, 140.0), at.plusSeconds(20))
        entityManager.clear()
        val statistics = entityManager.entityManagerFactory.unwrap(org.hibernate.SessionFactory::class.java).statistics
        val previousStatements = statistics.prepareStatementCount
        val readings = playerRepository.findByGameId(gameId)
        assertEquals(1L, statistics.prepareStatementCount - previousStatements)
        assertEquals(ids, readings.map { it.playerId })
        assertEquals(listOf(true, true, false), readings.map { it.confirmed })
        assertEquals(fixed, readings[0].confirmedLocation)
        assertEquals(Coordinate(36.0, 140.0), readings[0].live?.coordinate)
        assertEquals(Coordinate(35.591, 139.601), readings[1].confirmedLocation?.coordinate)
        assertNull(readings[1].live)
        assertNull(readings[2].confirmedLocation)
        playerRepository.updateLiveLocation(gameId, ids[0], Coordinate(37.0, 141.0), at.plusSeconds(40))
        assertEquals(fixed, playerRepository.findByGameId(gameId)[0].confirmedLocation)
    }

    @Test
    fun test_findByGameId_partialConfirmation_rejectsBothKindsOfMissingData() {
        for (assignment in listOf("confirmed_at = now()", "fixed_geom = ST_SetSRID(ST_MakePoint(139.6,35.59),4326)")) {
            val gameId = seedGame()
            val id = seedPlayer(gameId)
            jdbcTemplate.update("update player set $assignment where id = ?", id)
            entityManager.clear()
            val failure = assertThrows<org.springframework.dao.InvalidDataAccessApiUsageException> { playerRepository.findByGameId(gameId) }
            assertTrue(failure.mostSpecificCause is IllegalStateException)
        }
    }
}
