package com.github.gaomond.topo.adapter.persistence

import com.github.gaomond.topo.adapter.persistence.jpa.GameJpaRepository
import com.github.gaomond.topo.adapter.persistence.jpa.PlayerJpaRepository
import com.github.gaomond.topo.domain.model.AreaPreset
import com.github.gaomond.topo.domain.model.Game
import com.github.gaomond.topo.domain.model.ObjectType
import com.github.gaomond.topo.domain.model.Player
import com.github.gaomond.topo.support.PostgisTestContainer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** 外部キー循環を避けるゲーム・作成者の保存順と、対象不在・外部キー違反の検出を検証する。 */
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
    private lateinit var gameRepository: GameJpaRepository

    @Autowired
    private lateinit var playerRepository: PlayerJpaRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun test_createGame_viaAdapters_insertsGamePlayerAndLinksCreatorId() {
        val gameId = UUID.randomUUID()
        val playerId = UUID.randomUUID()

        gameRepository.createGame(Game.create(gameId, UUID.randomUUID(), 3, ObjectType.SHRINE, AreaPreset.ALL[1]))
        playerRepository.createPlayer(Player.create(playerId = playerId, gameId = gameId, displayName = "たろう"))
        gameRepository.updateCreatorPlayerId(gameId = gameId, creatorPlayerId = playerId)
        gameRepository.flush()
        playerRepository.flush()

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

        val player =
            jdbcTemplate.queryForMap(
                "SELECT game_id, display_name FROM player WHERE id = ?",
                playerId,
            )
        assertEquals(gameId, player["game_id"])
        assertEquals("たろう", player["display_name"])

        val summary = gameRepository.findSummary(gameId)
        assertEquals(2_000_000.0, summary?.areaThreshold)
    }

    @Test
    fun test_updateCreatorPlayerId_forMissingGame_throws() {
        val failure =
            assertThrows<DataAccessException> {
                gameRepository.updateCreatorPlayerId(
                    gameId = UUID.randomUUID(),
                    creatorPlayerId = UUID.randomUUID(),
                )
            }
        // Spring Data proxy が永続化例外へ翻訳し、対象不在の原因を保持する。
        assertIs<IllegalStateException>(failure.mostSpecificCause)
    }

    @Test
    fun test_createPlayer_withNonExistentGameId_failsByFk() {
        assertThrows<Exception> {
            playerRepository.createPlayer(
                Player.create(
                    playerId = UUID.randomUUID(),
                    gameId = UUID.randomUUID(),
                    displayName = "orphan",
                ),
            )
            playerRepository.flush()
        }
    }
}
