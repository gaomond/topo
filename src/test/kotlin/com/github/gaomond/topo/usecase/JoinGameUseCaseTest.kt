package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.exception.GameJoinNotAllowedException
import com.github.gaomond.topo.domain.exception.GameNotFoundException
import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.model.Game
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.GameSummary
import com.github.gaomond.topo.domain.model.ObjectType
import com.github.gaomond.topo.domain.model.Player
import com.github.gaomond.topo.domain.model.PlayerReading
import com.github.gaomond.topo.domain.port.GameRepositoryPort
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 定員境界と状態による参加拒否、拒否時に参加者を保存しないことを検証する。 */
class JoinGameUseCaseTest {
    private data class CreatedPlayer(
        val playerId: UUID,
        val gameId: UUID,
        val displayName: String,
    )

    private class SpyGameRepository(
        private val summary: GameSummary?,
    ) : GameRepositoryPort {
        override fun createWithoutCreator(game: Game) = error("参加では呼ばれない")

        override fun updateCreatorPlayerId(game: Game) = error("参加では呼ばれない")

        override fun updateStatus(game: Game) = error("参加では呼ばれない")

        override fun findSummary(gameId: UUID): GameSummary? = error("参加ではロック付き取得を使う")

        override fun findByIdWithLock(gameId: UUID): Game? =
            summary?.let {
                Game.restore(gameId, it.status, it.playerCount, it.creatorPlayerId, ObjectType.SHRINE, it.areaThreshold)
            }
    }

    private class SpyPlayerRepository(
        private val count: Int,
        val created: MutableList<CreatedPlayer> = mutableListOf(),
    ) : PlayerRepositoryPort {
        override fun createPlayer(player: Player) {
            created.add(CreatedPlayer(player.playerId, player.gameId, player.displayName))
        }

        override fun countByGameId(gameId: UUID): Int = count

        override fun findByGameId(gameId: UUID): List<PlayerReading> = error("参加では呼ばれない")

        override fun updateLiveLocation(
            gameId: UUID,
            playerId: UUID,
            coordinate: Coordinate,
            at: Instant,
        ): Boolean = error("参加では呼ばれない")
    }

    private fun useCase(
        summary: GameSummary?,
        count: Int = 0,
        playerRepo: SpyPlayerRepository = SpyPlayerRepository(count),
    ) = JoinGameUseCase(SpyGameRepository(summary), playerRepo)

    private val gameId = UUID.randomUUID()

    @Test
    fun test_join_withWaitingAndUnderCapacity_createsPlayerAndReturnsPlayerId() {
        val playerRepo = SpyPlayerRepository(count = 1)
        val result =
            useCase(
                GameSummary(GameStatus.WAITING, playerCount = 3, creatorPlayerId = null, areaThreshold = 2_000_000.0),
                playerRepo = playerRepo,
            ).join(JoinGameInput(gameId, displayName = "じろう"))

        assertEquals(1, playerRepo.created.size)
        val created = playerRepo.created[0]
        assertEquals(gameId, created.gameId)
        assertEquals(result.playerId, created.playerId)
        assertEquals("じろう", created.displayName)
    }

    @Test
    fun test_join_withUnknownGameId_throwsNotFound() {
        val playerRepo = SpyPlayerRepository(count = 0)
        assertThrows<GameNotFoundException> {
            useCase(summary = null, playerRepo = playerRepo).join(JoinGameInput(gameId))
        }
        assertTrue(playerRepo.created.isEmpty(), "不在時は INSERT しない")
    }

    @Test
    fun test_join_withActiveStatus_throwsJoinNotAllowed() {
        val playerRepo = SpyPlayerRepository(count = 0)
        assertThrows<GameJoinNotAllowedException> {
            useCase(GameSummary(GameStatus.ACTIVE, 3, creatorPlayerId = null, areaThreshold = 2_000_000.0), playerRepo = playerRepo)
                .join(JoinGameInput(gameId))
        }
        assertTrue(playerRepo.created.isEmpty())
    }

    @Test
    fun test_join_withCompletedStatus_throwsJoinNotAllowed() {
        val playerRepo = SpyPlayerRepository(count = 0)
        assertThrows<GameJoinNotAllowedException> {
            useCase(GameSummary(GameStatus.COMPLETED, 3, creatorPlayerId = null, areaThreshold = 2_000_000.0), playerRepo = playerRepo)
                .join(JoinGameInput(gameId))
        }
        assertTrue(playerRepo.created.isEmpty())
    }

    @Test
    fun test_join_atCapacity_throwsJoinNotAllowed() {
        // count == playerCount で満員。
        val playerRepo = SpyPlayerRepository(count = 3)
        assertThrows<GameJoinNotAllowedException> {
            useCase(GameSummary(GameStatus.WAITING, 3, creatorPlayerId = null, areaThreshold = 2_000_000.0), playerRepo = playerRepo)
                .join(JoinGameInput(gameId))
        }
        assertTrue(playerRepo.created.isEmpty())
    }

    @Test
    fun test_join_withBlankDisplayName_fallsBackToUuidPrefix8() {
        for (blank in listOf(null, "", "   ", "\t")) {
            val playerRepo = SpyPlayerRepository(count = 0)
            useCase(GameSummary(GameStatus.WAITING, 3, creatorPlayerId = null, areaThreshold = 2_000_000.0), playerRepo = playerRepo)
                .join(JoinGameInput(gameId, displayName = blank))
            val created = playerRepo.created[0]
            assertEquals(created.playerId.toString().take(8), created.displayName, "blank=[$blank]")
        }
    }

    @Test
    fun test_join_withDisplayName_persistsTrimmed() {
        val playerRepo = SpyPlayerRepository(count = 0)
        useCase(GameSummary(GameStatus.WAITING, 3, creatorPlayerId = null, areaThreshold = 2_000_000.0), playerRepo = playerRepo)
            .join(JoinGameInput(gameId, displayName = "  はなこ  "))
        assertEquals("はなこ", playerRepo.created[0].displayName)
    }

    @Test
    fun test_join_atCapacityMinusOne_isAccepted() {
        // count == playerCount - 1 は最後の 1 枠として参加可。
        val playerRepo = SpyPlayerRepository(count = 2)
        val result =
            useCase(GameSummary(GameStatus.WAITING, 3, creatorPlayerId = null, areaThreshold = 2_000_000.0), playerRepo = playerRepo)
                .join(JoinGameInput(gameId))
        assertNull(playerRepo.created.getOrNull(1))
        assertEquals(playerRepo.created[0].playerId, result.playerId)
    }
}
