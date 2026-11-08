package com.github.gaomond.topo.adapter.persistence

import com.github.gaomond.topo.domain.model.AreaPreset
import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.model.Game
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.ObjectType
import com.github.gaomond.topo.domain.model.Player
import com.github.gaomond.topo.domain.port.GameRepositoryPort
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import com.github.gaomond.topo.support.PostgisTestContainer
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Repository ポートとして注入された Spring Data proxy の変換・保存・トランザクションを検証する。 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RepositoryPortPersistenceTest {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            val container = PostgisTestContainer.instance
            registry.add("spring.datasource.url") { container.jdbcUrl }
            registry.add("spring.datasource.username") { container.username }
            registry.add("spring.datasource.password") { container.password }
        }
    }

    @Autowired
    private lateinit var games: GameRepositoryPort

    @Autowired
    private lateinit var players: PlayerRepositoryPort

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var transactions: PlatformTransactionManager

    @Test
    fun test_findByIdWithLock_existingGame_restoresDomainModelWithNullableCreator() {
        val gameId = seedGame()
        entityManager.flush()
        entityManager.clear()

        val restored = games.findByIdWithLock(gameId)
        assertEquals(gameId, restored?.gameId)
        assertEquals(GameStatus.WAITING, restored?.status)
        assertEquals(3, restored?.playerCount)
        assertNull(restored?.creatorPlayerId)
        assertEquals(ObjectType.SHRINE, restored?.objectType)
        assertEquals(2_000_000.0, restored?.areaThreshold)
        assertEquals(2_000_000.0, games.findSummary(gameId)?.areaThreshold)
        assertNull(games.findByIdWithLock(UUID.randomUUID()))
        assertNull(games.findSummary(UUID.randomUUID()))
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun test_findByIdWithLock_withoutTransaction_rejectsCall() {
        assertThrows<IllegalTransactionStateException> {
            games.findByIdWithLock(UUID.randomUUID())
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun test_findByIdWithLock_readOnlyTransaction_rejectsLock() {
        val transaction = TransactionTemplate(transactions).apply { isReadOnly = true }
        val failure =
            assertThrows<DataAccessException> {
                transaction.executeWithoutResult { games.findByIdWithLock(UUID.randomUUID()) }
            }
        assertEquals("25006", (failure.mostSpecificCause as SQLException).sqlState)
    }

    @Test
    fun test_updateStatus_existingResults_preservesOtherColumnsAndPolygon() {
        val gameId = seedGame()
        val creator = UUID.randomUUID()
        players.createPlayer(Player.create(creator, gameId, "作成者"))
        games.updateCreatorPlayerId(Game.create(gameId, creator, 3, ObjectType.SHRINE, AreaPreset.ALL[1]))
        entityManager.flush()
        jdbc.update(
            """
            UPDATE game SET area_sqm = 12.5, area_valid = true, object_count = 7,
                polygon_geom = ST_GeomFromText('POLYGON((0 0,1 0,0 1,0 0))',4326)
            WHERE id = ?
            """.trimIndent(),
            gameId,
        )
        val before = jdbc.queryForMap("SELECT *, ST_AsText(polygon_geom) AS polygon FROM game WHERE id = ?", gameId)
        entityManager.clear()

        games.updateStatus(Game.restore(gameId, GameStatus.ACTIVE, 99, null, ObjectType.TEMPLE, 123.5))
        entityManager.flush()
        entityManager.clear()

        val after = jdbc.queryForMap("SELECT *, ST_AsText(polygon_geom) AS polygon FROM game WHERE id = ?", gameId)
        assertEquals("ACTIVE", after["status"].toString())
        for (column in listOf(
            "player_count",
            "object_type",
            "area_threshold",
            "creator_player_id",
            "created_at",
            "area_sqm",
            "area_valid",
            "object_count",
            "polygon",
        )) {
            assertEquals(before[column], after[column], column)
        }
    }

    @Test
    fun test_updateLiveLocation_confirmedPlayer_preservesFixedLocationAndOtherColumns() {
        val gameId = seedGame()
        val playerId = UUID.randomUUID()
        players.createPlayer(Player.create(playerId, gameId, "プレイヤー"))
        entityManager.flush()
        jdbc.update(
            "UPDATE player SET fixed_geom = ST_SetSRID(ST_MakePoint(139.0,35.0),4326), confirmed_at = ? WHERE id = ?",
            java.sql.Timestamp.from(Instant.parse("2026-11-01T01:00:00Z")),
            playerId,
        )
        val before = jdbc.queryForMap("SELECT *, ST_AsText(fixed_geom) AS fixed FROM player WHERE id = ?", playerId)
        entityManager.clear()
        val at = Instant.parse("2026-11-08T01:00:00Z")

        assertTrue(players.updateLiveLocation(gameId, playerId, Coordinate(35.68, 139.76), at))
        entityManager.flush()
        entityManager.clear()

        val after = jdbc.queryForMap("SELECT *, ST_AsText(fixed_geom) AS fixed FROM player WHERE id = ?", playerId)
        assertEquals(35.68, after["live_lat"])
        assertEquals(139.76, after["live_lng"])
        assertEquals(java.sql.Timestamp.from(at), after["live_at"])
        for (column in listOf("game_id", "display_name", "joined_at", "confirmed_at", "fixed")) {
            assertEquals(before[column], after[column], column)
        }
        assertEquals(1, players.countByGameId(gameId))
        val reading = players.findByGameId(gameId).single()
        assertEquals(playerId, reading.playerId)
        assertTrue(reading.confirmed)
        assertEquals(at, reading.live?.at)
    }

    @Test
    fun test_findByIdWithLock_savedStates_restoresStoredSettingsAndNonSelectableType() {
        for (status in GameStatus.entries) {
            val gameId = seedGame()
            entityManager.flush()
            jdbc.update(
                "UPDATE game SET status = ?::game_status, object_type = 'temple', area_threshold = 123.5 WHERE id = ?",
                status.name,
                gameId,
            )
            entityManager.clear()
            val restored = requireNotNull(games.findByIdWithLock(gameId))
            assertEquals(status, restored.status)
            assertEquals(ObjectType.TEMPLE, restored.objectType)
            assertEquals(123.5, restored.areaThreshold)
        }
    }

    @Test
    fun test_updateCreatorPlayerId_differentModelFields_changesOnlyCreator() {
        val gameId = seedGame()
        val creator = UUID.randomUUID()
        players.createPlayer(Player.create(creator, gameId, "作成者"))
        entityManager.flush()
        jdbc.update(
            "UPDATE game SET area_sqm = 12.5, area_valid = true, object_count = 7, " +
                "polygon_geom = ST_GeomFromText('POLYGON((0 0,1 0,0 1,0 0))',4326) WHERE id = ?",
            gameId,
        )
        val before = jdbc.queryForMap("SELECT *, ST_AsText(polygon_geom) AS polygon FROM game WHERE id = ?", gameId)
        entityManager.clear()

        games.updateCreatorPlayerId(Game.restore(gameId, GameStatus.COMPLETED, 99, creator, ObjectType.TEMPLE, 123.5))
        entityManager.flush()
        entityManager.clear()

        val after = jdbc.queryForMap("SELECT *, ST_AsText(polygon_geom) AS polygon FROM game WHERE id = ?", gameId)
        assertEquals(creator, after["creator_player_id"])
        for (column in listOf(
            "status",
            "player_count",
            "object_type",
            "area_threshold",
            "created_at",
            "area_sqm",
            "area_valid",
            "object_count",
            "polygon",
        )) {
            assertEquals(before[column], after[column], column)
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun test_updateCreatorPlayerId_nullCreator_rejectsWithoutChangingSavedGame() {
        val transaction = TransactionTemplate(transactions)
        val gameId = requireNotNull(transaction.execute { seedGame() })
        try {
            val before = transaction.execute { jdbc.queryForMap("SELECT * FROM game WHERE id = ?", gameId) }
            val failure =
                assertThrows<DataAccessException> {
                    transaction.executeWithoutResult {
                        games.updateCreatorPlayerId(Game.restore(gameId, GameStatus.ACTIVE, 99, null, ObjectType.TEMPLE, 123.5))
                    }
                }
            kotlin.test.assertIs<IllegalStateException>(failure.mostSpecificCause)
            val after = transaction.execute { jdbc.queryForMap("SELECT * FROM game WHERE id = ?", gameId) }
            assertEquals(before, after)
        } finally {
            transaction.executeWithoutResult { jdbc.update("DELETE FROM game WHERE id = ?", gameId) }
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun test_updateStatus_missingGame_rejectsWithoutCreatingGame() {
        val gameId = UUID.randomUUID()
        val failure =
            assertThrows<DataAccessException> {
                games.updateStatus(Game.restore(gameId, GameStatus.ACTIVE, 3, null, ObjectType.SHRINE, 123.5))
            }
        kotlin.test.assertIs<IllegalStateException>(failure.mostSpecificCause)
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM game WHERE id = ?", Int::class.java, gameId))
    }

    private fun seedGame(): UUID =
        UUID.randomUUID().also { gameId ->
            games.createWithoutCreator(Game.create(gameId, UUID.randomUUID(), 3, ObjectType.SHRINE, AreaPreset.ALL[1]))
        }
}
