package com.github.gaomond.topo.adapter.persistence

import com.github.gaomond.topo.adapter.persistence.jpa.GameJpaRepository
import com.github.gaomond.topo.adapter.persistence.jpa.PlayerJpaRepository
import com.github.gaomond.topo.domain.model.Game
import com.github.gaomond.topo.domain.model.Player
import com.github.gaomond.topo.domain.port.GameRepositoryPort
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import com.github.gaomond.topo.support.PostgisTestContainer
import com.github.gaomond.topo.usecase.CreateGameInput
import com.github.gaomond.topo.usecase.CreateGameUseCase
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CreateGameUseCase::class, GameCreationTransactionTest.Configuration::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GameCreationTransactionTest {
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

    class FailurePoint {
        var stage = ""
        var gameId: UUID? = null
        var playerId: UUID? = null
    }

    @TestConfiguration
    class Configuration {
        @Bean
        fun failurePoint() = FailurePoint()

        @Bean
        @Primary
        fun games(
            repository: GameJpaRepository,
            failure: FailurePoint,
        ): GameRepositoryPort =
            object : GameRepositoryPort by repository {
                override fun createGame(game: Game) {
                    failure.gameId = game.gameId
                    repository.createGame(game)
                    repository.flush()
                }

                override fun updateCreatorPlayerId(
                    gameId: UUID,
                    creatorPlayerId: UUID,
                ) {
                    repository.updateCreatorPlayerId(gameId, creatorPlayerId)
                    repository.flush()
                    if (failure.stage == "link") error("紐付け後の失敗")
                }
            }

        @Bean
        @Primary
        fun players(
            repository: PlayerJpaRepository,
            failure: FailurePoint,
        ): PlayerRepositoryPort =
            object : PlayerRepositoryPort by repository {
                override fun createPlayer(player: Player) {
                    failure.playerId = player.playerId
                    repository.createPlayer(player)
                    repository.flush()
                    if (failure.stage == "player") error("作成者保存後の失敗")
                }
            }
    }

    @Autowired
    private lateinit var useCase: CreateGameUseCase

    @Autowired
    private lateinit var failure: FailurePoint

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var transactions: PlatformTransactionManager

    @Test
    fun test_create_validInput_commitsGameCreatorAndInitialColumns() {
        failure.stage = ""
        val result = useCase.create(CreateGameInput("shrine", "small", 4, " たろう "))
        try {
            TransactionTemplate(transactions).executeWithoutResult {
                val game = jdbc.queryForMap("SELECT *, ST_AsText(polygon_geom) AS polygon FROM game WHERE id = ?", result.gameId)
                assertEquals("WAITING", game["status"].toString())
                assertEquals(4, game["player_count"])
                assertEquals("shrine", game["object_type"])
                assertEquals(500_000.0, game["area_threshold"])
                assertEquals(result.playerId, game["creator_player_id"])
                for (column in listOf("polygon", "area_sqm", "area_valid", "object_count")) assertNull(game[column])
                val player = jdbc.queryForMap("SELECT *, ST_AsText(fixed_geom) AS fixed FROM player WHERE id = ?", result.playerId)
                assertEquals(result.gameId, player["game_id"])
                assertEquals("たろう", player["display_name"])
                for (column in listOf("live_lat", "live_lng", "live_at", "fixed", "confirmed_at")) assertNull(player[column])
            }
        } finally {
            TransactionTemplate(transactions).executeWithoutResult {
                jdbc.update("UPDATE game SET creator_player_id = NULL WHERE id = ?", result.gameId)
                jdbc.update("DELETE FROM player WHERE game_id = ?", result.gameId)
                jdbc.update("DELETE FROM game WHERE id = ?", result.gameId)
            }
        }
    }

    @Test
    fun test_create_failureAfterFlushedWrites_rollsBackGameAndPlayer() {
        for (stage in listOf("player", "link")) {
            failure.stage = stage
            assertThrows<IllegalStateException> {
                useCase.create(CreateGameInput("shrine", "medium", 3, null))
            }
            // UseCaseのTxが終了した後、別のTxから確認する。
            TransactionTemplate(transactions).executeWithoutResult {
                assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM game WHERE id = ?", Int::class.java, failure.gameId))
                assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM player WHERE id = ?", Int::class.java, failure.playerId))
            }
        }
    }
}
