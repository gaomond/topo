package com.github.gaomond.topo.adapter.persistence

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
import kotlin.test.assertNull

/**
 * US-05 Level 3（outbound アダプタ）検証。実 PostGIS（Testcontainers）に対し、
 * 参加判定用の取得系（findSummary / countByGameId / findByGameId）を検証する。
 * テスト対象は src/ の [GameRepositoryAdapter] / [PlayerRepositoryAdapter] を import（再定義しない）。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GameJoinPersistenceTest {
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
    private lateinit var gameRepository: GameRepository

    @Autowired
    private lateinit var playerRepository: PlayerRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    // 生 SQL（jdbcTemplate）で DB を直接更新した後、JPA の 1 次キャッシュを破棄して
    // 再取得が DB の最新値を反映するようにする（confirmed_at / joined_at の直接更新用）。
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private fun gameAdapter() = GameRepositoryAdapter(gameRepository)

    private fun playerAdapter() = PlayerRepositoryAdapter(playerRepository)

    private fun seedGame(
        status: GameStatus = GameStatus.WAITING,
        playerCount: Int = 3,
    ): UUID {
        val gameId = UUID.randomUUID()
        gameAdapter().createGame(
            gameId = gameId,
            status = status,
            playerCount = playerCount,
            objectType = "shrine",
            areaThreshold = 2_000_000.0,
        )
        gameRepository.flush()
        return gameId
    }

    @Test
    fun test_findSummary_withExistingGame_returnsStatusAndPlayerCount() {
        val gameId = seedGame(status = GameStatus.ACTIVE, playerCount = 4)
        val summary = gameAdapter().findSummary(gameId)
        assertEquals(GameStatus.ACTIVE, summary?.status)
        assertEquals(4, summary?.playerCount)
    }

    @Test
    fun test_findSummary_withUnknownId_returnsNull() {
        assertNull(gameAdapter().findSummary(UUID.randomUUID()))
    }

    @Test
    fun test_countByGameId_returnsNumberOfPlayers() {
        val gameId = seedGame()
        repeat(2) {
            playerAdapter().createPlayer(UUID.randomUUID(), gameId, "p$it")
        }
        playerRepository.flush()
        assertEquals(2, playerAdapter().countByGameId(gameId))
    }

    @Test
    fun test_findByGameId_returnsOnlyPlayersOfThatGameInJoinOrder() {
        val gameA = seedGame()
        val gameB = seedGame()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        playerAdapter().createPlayer(first, gameA, "さき")
        playerAdapter().createPlayer(second, gameA, "あと")
        playerAdapter().createPlayer(UUID.randomUUID(), gameB, "べつ")
        playerRepository.flush()
        // joined_at を明示的に前後させて参加順を保証する。
        jdbcTemplate.update(
            "UPDATE player SET joined_at = ? WHERE id = ?",
            Instant.parse("2026-01-01T00:00:00Z").let { java.sql.Timestamp.from(it) },
            first,
        )
        jdbcTemplate.update(
            "UPDATE player SET joined_at = ? WHERE id = ?",
            Instant.parse("2026-01-01T00:00:01Z").let { java.sql.Timestamp.from(it) },
            second,
        )
        entityManager.clear()

        val players = playerAdapter().findByGameId(gameA)

        assertEquals(2, players.size)
        assertEquals(first, players[0].playerId)
        assertEquals(second, players[1].playerId)
        assertEquals("さき", players[0].displayName)
    }

    @Test
    fun test_findByGameId_reflectsConfirmedFromConfirmedAt() {
        val gameId = seedGame()
        val confirmedPlayer = UUID.randomUUID()
        val pendingPlayer = UUID.randomUUID()
        playerAdapter().createPlayer(confirmedPlayer, gameId, "かくてい")
        playerAdapter().createPlayer(pendingPlayer, gameId, "みかくてい")
        playerRepository.flush()
        jdbcTemplate.update(
            "UPDATE player SET confirmed_at = now() WHERE id = ?",
            confirmedPlayer,
        )
        entityManager.clear()

        val players = playerAdapter().findByGameId(gameId).associateBy { it.playerId }

        assertEquals(true, players[confirmedPlayer]?.confirmed)
        assertEquals(false, players[pendingPlayer]?.confirmed)
    }
}
