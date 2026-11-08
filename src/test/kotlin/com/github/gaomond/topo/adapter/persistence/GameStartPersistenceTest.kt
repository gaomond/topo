package com.github.gaomond.topo.adapter.persistence

import com.github.gaomond.topo.adapter.persistence.jpa.GameJpaRepository
import com.github.gaomond.topo.adapter.persistence.jpa.PlayerJpaRepository
import com.github.gaomond.topo.domain.model.AreaPreset
import com.github.gaomond.topo.domain.model.Game
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.ObjectType
import com.github.gaomond.topo.domain.model.Player
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

/** 開始時の status 部分更新で他カラムを維持し、作成者を読み取れることを検証する。 */
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

    private fun seedGameWithCreator(playerCount: Int = 3): Pair<UUID, UUID> {
        val gameId = UUID.randomUUID()
        gameRepository.createWithoutCreator(Game.create(gameId, UUID.randomUUID(), playerCount, ObjectType.SHRINE, AreaPreset.ALL[1]))
        gameRepository.flush()
        val creatorPlayerId = UUID.randomUUID()
        playerRepository.createPlayer(Player.create(creatorPlayerId, gameId, "さくせいしゃ"))
        playerRepository.flush()
        gameRepository.updateCreatorPlayerId(Game.create(gameId, creatorPlayerId, playerCount, ObjectType.SHRINE, AreaPreset.ALL[1]))
        gameRepository.flush()
        entityManager.clear()
        return gameId to creatorPlayerId
    }

    @Test
    fun test_updateStatus_changesOnlyStatusColumn() {
        val (gameId, creatorPlayerId) = seedGameWithCreator(playerCount = 3)

        gameRepository.updateStatus(Game.restore(gameId, GameStatus.ACTIVE, 99, null, ObjectType.TEMPLE, 123.5))
        gameRepository.flush()
        entityManager.clear()

        val updated = requireNotNull(gameRepository.findRecordById(gameId))
        assertEquals(GameStatus.ACTIVE, updated.status)
        assertEquals(3, updated.playerCount)
        assertEquals("shrine", updated.objectType)
        assertEquals(2_000_000.0, updated.areaThreshold)
        assertEquals(creatorPlayerId, updated.creatorPlayerId)
    }

    @Test
    fun test_findSummary_returnsCreatorPlayerId() {
        val (gameId, creatorPlayerId) = seedGameWithCreator()

        val summary = gameRepository.findSummary(gameId)

        assertEquals(creatorPlayerId, summary?.creatorPlayerId)
        assertEquals(GameStatus.WAITING, summary?.status)
        assertEquals(3, summary?.playerCount)
    }
}
