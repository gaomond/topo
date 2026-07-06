package com.github.gaomond.topo.adapter.persistence

import com.github.gaomond.topo.adapter.persistence.jpa.GameJpaRepository
import com.github.gaomond.topo.adapter.persistence.jpa.PlayerJpaRepository
import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.support.PostgisTestContainer
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
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

/**
 * US-07 Level 3（outbound アダプタ）検証。実 PostGIS（Testcontainers）に対し、
 * ライブ位置更新（一致つき UPDATE / 所属不一致で 0 件 / live_at の反映）を検証する。
 * テスト対象は src/ の [PlayerRepositoryAdapter] を import（再定義しない）。
 */
@DataJpaTest
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

    private fun gameAdapter() = GameRepositoryAdapter(gameRepository)

    private fun playerAdapter() = PlayerRepositoryAdapter(playerRepository)

    private fun seedGame(status: GameStatus = GameStatus.ACTIVE): UUID {
        val gameId = UUID.randomUUID()
        gameAdapter().createGame(
            gameId = gameId,
            status = status,
            playerCount = 3,
            objectType = "shrine",
            areaThreshold = 2_000_000.0,
        )
        gameRepository.flush()
        return gameId
    }

    private fun seedPlayer(gameId: UUID): UUID {
        val playerId = UUID.randomUUID()
        playerAdapter().createPlayer(playerId, gameId, "プレイヤー")
        playerRepository.flush()
        return playerId
    }

    @Test
    fun test_updateLiveLocation_withMatchingPlayer_updatesLiveColumns() {
        val gameId = seedGame()
        val playerId = seedPlayer(gameId)
        val at = Instant.parse("2026-07-04T12:00:00Z")

        val updated =
            playerAdapter().updateLiveLocation(gameId, playerId, Coordinate(35.68, 139.76), at)
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

        // playerId は gameId に属するが、otherGameId を指定すると一致せず 0 件。
        val updated =
            playerAdapter().updateLiveLocation(otherGameId, playerId, Coordinate(1.0, 2.0), Instant.now())
        entityManager.clear()

        assertFalse(updated)
        val row =
            jdbcTemplate.queryForMap(
                "SELECT live_lat, live_lng FROM player WHERE id = ?",
                playerId,
            )
        // 一致しないので live_* は更新されない（NULL のまま）。
        assertNull(row["live_lat"])
        assertNull(row["live_lng"])
    }

    @Test
    fun test_updateLiveLocation_withUnknownPlayer_updatesZeroRows() {
        val gameId = seedGame()
        val updated =
            playerAdapter().updateLiveLocation(gameId, UUID.randomUUID(), Coordinate(1.0, 2.0), Instant.now())
        assertFalse(updated)
    }

    @Test
    fun `test_findByGameId_live送信済み_LiveLocationを射影`() {
        val gameId = seedGame()
        val playerId = seedPlayer(gameId)
        val at = Instant.parse("2026-07-04T12:00:00Z")
        playerAdapter().updateLiveLocation(gameId, playerId, Coordinate(35.68, 139.76), at)
        entityManager.clear()

        val reading = playerAdapter().findByGameId(gameId).single()

        assertEquals(35.68, reading.live?.coordinate?.lat)
        assertEquals(139.76, reading.live?.coordinate?.lng)
        assertEquals(at, reading.live?.at)
    }

    @Test
    fun `test_findByGameId_live未送信_liveはnull`() {
        val gameId = seedGame()
        seedPlayer(gameId)
        entityManager.clear()

        val reading = playerAdapter().findByGameId(gameId).single()

        assertNull(reading.live)
    }

    @Test
    fun test_updateLiveLocation_setsLiveAtToGivenInstant() {
        val gameId = seedGame()
        val playerId = seedPlayer(gameId)
        val at = Instant.parse("2026-07-04T09:30:00Z")

        playerAdapter().updateLiveLocation(gameId, playerId, Coordinate(10.0, 20.0), at)
        entityManager.clear()

        val storedAt =
            jdbcTemplate.queryForObject(
                "SELECT live_at FROM player WHERE id = ?",
                java.sql.Timestamp::class.java,
                playerId,
            )
        assertEquals(at, storedAt?.toInstant())
    }
}
