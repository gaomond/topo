package com.github.gaomond.topo.adapter.persistence

import com.github.gaomond.topo.adapter.persistence.jpa.GameJpaRepository
import com.github.gaomond.topo.adapter.persistence.jpa.PlayerJpaRepository
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.support.PostgisTestContainer
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.UUID
import kotlin.test.assertEquals

/**
 * US-06 Level 3（outbound アダプタ）検証。実 PostGIS（Testcontainers）に対し、
 * 開始処理の永続化（updateStatus が status のみ更新 / findSummary の creatorPlayerId 射影）を検証する。
 * テスト対象は src/ の [GameRepositoryAdapter] / [PlayerRepositoryAdapter] を import（再定義しない）。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GameStartPersistenceTest {
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

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private fun gameAdapter() = GameRepositoryAdapter(gameRepository)

    private fun playerAdapter() = PlayerRepositoryAdapter(playerRepository)

    // creator を設定した WAITING ゲームを作る（game→player→creator UPDATE の創成順序）。
    private fun seedGameWithCreator(playerCount: Int = 3): Pair<UUID, UUID> {
        val gameId = UUID.randomUUID()
        gameAdapter().createGame(
            gameId = gameId,
            status = GameStatus.WAITING,
            playerCount = playerCount,
            objectType = "shrine",
            areaThreshold = 2_000_000.0,
        )
        gameRepository.flush()
        val creatorPlayerId = UUID.randomUUID()
        playerAdapter().createPlayer(creatorPlayerId, gameId, "さくせいしゃ")
        playerRepository.flush()
        gameAdapter().updateCreatorPlayerId(gameId, creatorPlayerId)
        gameRepository.flush()
        entityManager.clear()
        return gameId to creatorPlayerId
    }

    @Test
    fun test_updateStatus_changesOnlyStatusColumn() {
        val (gameId, creatorPlayerId) = seedGameWithCreator(playerCount = 3)

        gameAdapter().updateStatus(gameId, GameStatus.ACTIVE)
        gameRepository.flush()
        entityManager.clear()

        val updated = gameRepository.findById(gameId).orElseThrow()
        assertEquals(GameStatus.ACTIVE, updated.status)
        // 他カラムは不変。
        assertEquals(3, updated.playerCount)
        assertEquals("shrine", updated.objectType)
        assertEquals(2_000_000.0, updated.areaThreshold)
        assertEquals(creatorPlayerId, updated.creatorPlayerId)
    }

    @Test
    fun test_findSummary_returnsCreatorPlayerId() {
        val (gameId, creatorPlayerId) = seedGameWithCreator()

        val summary = gameAdapter().findSummary(gameId)

        assertEquals(creatorPlayerId, summary?.creatorPlayerId)
        assertEquals(GameStatus.WAITING, summary?.status)
        assertEquals(3, summary?.playerCount)
    }
}
