package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.exception.GameJoinNotAllowedException
import com.github.gaomond.topo.domain.exception.GameNotFoundException
import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.GameSummary
import com.github.gaomond.topo.domain.model.JoinGameCommand
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

/**
 * US-05 Level 2（UseCase）検証。ポートは手書きの spy で差し替える（外部モックライブラリ非依存）。
 * 存在確認 / 状態判定 / 定員判定 / displayName フォールバック / 拒否時未 INSERT を検証する。
 * テスト対象は src/ の [JoinGameUseCase] を import（再定義しない）。
 */
class JoinGameUseCaseTest {
    private data class CreatedPlayer(
        val playerId: UUID,
        val gameId: UUID,
        val displayName: String,
    )

    private class SpyGameRepository(
        private val summary: GameSummary?,
    ) : GameRepositoryPort {
        override fun createGame(
            gameId: UUID,
            status: GameStatus,
            playerCount: Int,
            objectType: String,
            areaThreshold: Double,
        ) = error("参加では呼ばれない")

        override fun updateCreatorPlayerId(
            gameId: UUID,
            creatorPlayerId: UUID,
        ) = error("参加では呼ばれない")

        override fun updateStatus(
            gameId: UUID,
            status: GameStatus,
        ) = error("参加では呼ばれない")

        override fun findSummary(gameId: UUID): GameSummary? = summary
    }

    private class SpyPlayerRepository(
        private val count: Int,
        val created: MutableList<CreatedPlayer> = mutableListOf(),
    ) : PlayerRepositoryPort {
        override fun createPlayer(
            playerId: UUID,
            gameId: UUID,
            displayName: String,
        ) {
            created.add(CreatedPlayer(playerId, gameId, displayName))
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
            useCase(GameSummary(GameStatus.WAITING, playerCount = 3, creatorPlayerId = null), playerRepo = playerRepo)
                .join(JoinGameCommand(gameId, displayName = "じろう"))

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
            useCase(summary = null, playerRepo = playerRepo).join(JoinGameCommand(gameId))
        }
        assertTrue(playerRepo.created.isEmpty(), "不在時は INSERT しない")
    }

    @Test
    fun test_join_withActiveStatus_throwsJoinNotAllowed() {
        val playerRepo = SpyPlayerRepository(count = 0)
        assertThrows<GameJoinNotAllowedException> {
            useCase(GameSummary(GameStatus.ACTIVE, 3, creatorPlayerId = null), playerRepo = playerRepo)
                .join(JoinGameCommand(gameId))
        }
        assertTrue(playerRepo.created.isEmpty())
    }

    @Test
    fun test_join_withCompletedStatus_throwsJoinNotAllowed() {
        val playerRepo = SpyPlayerRepository(count = 0)
        assertThrows<GameJoinNotAllowedException> {
            useCase(GameSummary(GameStatus.COMPLETED, 3, creatorPlayerId = null), playerRepo = playerRepo)
                .join(JoinGameCommand(gameId))
        }
        assertTrue(playerRepo.created.isEmpty())
    }

    @Test
    fun test_join_atCapacity_throwsJoinNotAllowed() {
        // count == playerCount で満員。
        val playerRepo = SpyPlayerRepository(count = 3)
        assertThrows<GameJoinNotAllowedException> {
            useCase(GameSummary(GameStatus.WAITING, 3, creatorPlayerId = null), playerRepo = playerRepo)
                .join(JoinGameCommand(gameId))
        }
        assertTrue(playerRepo.created.isEmpty())
    }

    @Test
    fun test_join_withBlankDisplayName_fallsBackToUuidPrefix8() {
        for (blank in listOf(null, "", "   ", "\t")) {
            val playerRepo = SpyPlayerRepository(count = 0)
            useCase(GameSummary(GameStatus.WAITING, 3, creatorPlayerId = null), playerRepo = playerRepo)
                .join(JoinGameCommand(gameId, displayName = blank))
            val created = playerRepo.created[0]
            assertEquals(created.playerId.toString().take(8), created.displayName, "blank=[$blank]")
        }
    }

    @Test
    fun test_join_withDisplayName_persistsTrimmed() {
        val playerRepo = SpyPlayerRepository(count = 0)
        useCase(GameSummary(GameStatus.WAITING, 3, creatorPlayerId = null), playerRepo = playerRepo)
            .join(JoinGameCommand(gameId, displayName = "  はなこ  "))
        assertEquals("はなこ", playerRepo.created[0].displayName)
    }

    @Test
    fun test_join_atCapacityMinusOne_isAccepted() {
        // count == playerCount - 1 は最後の 1 枠として参加可。
        val playerRepo = SpyPlayerRepository(count = 2)
        val result =
            useCase(GameSummary(GameStatus.WAITING, 3, creatorPlayerId = null), playerRepo = playerRepo)
                .join(JoinGameCommand(gameId))
        assertNull(playerRepo.created.getOrNull(1))
        assertEquals(playerRepo.created[0].playerId, result.playerId)
    }
}
