package com.github.gaomond.topo.adapter.persistence

import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.support.PostgisTestContainer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * US-04 Level 3（outbound アダプタ）検証。実 PostGIS（Testcontainers）に対し、
 * 創成順序（game INSERT → player INSERT → creator UPDATE）の 3 テーブル操作整合と
 * 途中失敗のロールバックを検証する。テスト対象は src/ の [GameRepositoryAdapter] /
 * [PlayerRepositoryAdapter] を import（再定義しない）。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GameCreationPersistenceTest {
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

    private fun gameAdapter() = GameRepositoryAdapter(gameRepository)

    private fun playerAdapter() = PlayerRepositoryAdapter(playerRepository)

    @Test
    fun test_createGame_viaAdapters_insertsGamePlayerAndLinksCreatorId() {
        val gameId = UUID.randomUUID()
        val playerId = UUID.randomUUID()

        // 創成順序を UseCase と同順で adapter 経由に実行する。
        gameAdapter().createGame(
            gameId = gameId,
            status = GameStatus.WAITING,
            playerCount = 3,
            objectType = "shrine",
            areaThreshold = 2_000_000.0,
        )
        playerAdapter().createPlayer(playerId = playerId, gameId = gameId, displayName = "たろう")
        gameAdapter().updateCreatorPlayerId(gameId = gameId, creatorPlayerId = playerId)
        gameRepository.flush()
        playerRepository.flush()

        // game: WAITING / 結果カラム NULL / creator リンク済み
        val game =
            jdbcTemplate.queryForMap(
                """
                SELECT status::text AS status, player_count, object_type, area_threshold,
                       creator_player_id, area_sqm, area_valid, object_count
                FROM game WHERE id = ?
                """.trimIndent(),
                gameId,
            )
        assertEquals("WAITING", game["status"])
        assertEquals(3, game["player_count"])
        assertEquals("shrine", game["object_type"])
        assertEquals(2_000_000.0, game["area_threshold"])
        assertEquals(playerId, game["creator_player_id"])
        assertNull(game["area_sqm"])
        assertNull(game["area_valid"])
        assertNull(game["object_count"])

        // player: game_id 埋め・displayName 保存
        val player =
            jdbcTemplate.queryForMap(
                "SELECT game_id, display_name FROM player WHERE id = ?",
                playerId,
            )
        assertEquals(gameId, player["game_id"])
        assertEquals("たろう", player["display_name"])
    }

    @Test
    fun test_updateCreatorPlayerId_forMissingGame_throws() {
        // 創成 UPDATE の対象 game が存在しない場合は例外（ロールバックのトリガになりうる）。
        assertThrows<IllegalStateException> {
            gameAdapter().updateCreatorPlayerId(
                gameId = UUID.randomUUID(),
                creatorPlayerId = UUID.randomUUID(),
            )
        }
    }

    @Test
    fun test_createPlayer_withNonExistentGameId_failsByFk() {
        // player の game_id FK 違反時は例外になる（途中失敗のトリガ。ロールバックは UseCase の @Transactional 責務）。
        assertThrows<Exception> {
            playerAdapter().createPlayer(
                playerId = UUID.randomUUID(),
                gameId = UUID.randomUUID(),
                displayName = "orphan",
            )
            playerRepository.flush()
        }
    }
}
