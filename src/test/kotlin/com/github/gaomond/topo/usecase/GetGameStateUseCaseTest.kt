package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.GameNotFoundException
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.GameSummary
import com.github.gaomond.topo.domain.model.PlayerSnapshot
import com.github.gaomond.topo.domain.port.GameRepositoryPort
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals

/**
 * US-05 Level 2（UseCase）検証。状態取得の最小形 / 404 / players 射影を検証する。
 * テスト対象は src/ の [GetGameStateUseCase] を import（再定義しない）。
 */
class GetGameStateUseCaseTest {
    private class SpyGameRepository(
        private val summary: GameSummary?,
    ) : GameRepositoryPort {
        override fun createGame(
            gameId: UUID,
            status: GameStatus,
            playerCount: Int,
            objectType: String,
            areaThreshold: Double,
        ) = error("状態取得では呼ばれない")

        override fun updateCreatorPlayerId(
            gameId: UUID,
            creatorPlayerId: UUID,
        ) = error("状態取得では呼ばれない")

        override fun findSummary(gameId: UUID): GameSummary? = summary
    }

    private class StubPlayerRepository(
        private val players: List<PlayerSnapshot>,
    ) : PlayerRepositoryPort {
        override fun createPlayer(
            playerId: UUID,
            gameId: UUID,
            displayName: String,
        ) = error("状態取得では呼ばれない")

        override fun countByGameId(gameId: UUID): Int = error("状態取得では呼ばれない")

        override fun findByGameId(gameId: UUID): List<PlayerSnapshot> = players
    }

    private val gameId = UUID.randomUUID()

    @Test
    fun test_getGameState_withExistingGame_returnsStatusPlayerCountAndPlayers() {
        val players =
            listOf(
                PlayerSnapshot(UUID.randomUUID(), "たろう", confirmed = true),
                PlayerSnapshot(UUID.randomUUID(), "じろう", confirmed = false),
            )
        val useCase =
            GetGameStateUseCase(
                SpyGameRepository(GameSummary(GameStatus.WAITING, playerCount = 3)),
                StubPlayerRepository(players),
            )

        val state = useCase.getState(gameId)

        assertEquals(gameId, state.gameId)
        assertEquals(GameStatus.WAITING, state.status)
        assertEquals(3, state.playerCount)
        assertEquals(players, state.players)
    }

    @Test
    fun test_getGameState_withUnknownGameId_throwsNotFound() {
        val useCase =
            GetGameStateUseCase(SpyGameRepository(summary = null), StubPlayerRepository(emptyList()))
        assertThrows<GameNotFoundException> { useCase.getState(gameId) }
    }

    @Test
    fun test_getGameState_reflectsConfirmedFlagFromPlayers() {
        val confirmed = PlayerSnapshot(UUID.randomUUID(), "かくてい", confirmed = true)
        val notYet = PlayerSnapshot(UUID.randomUUID(), "みかくてい", confirmed = false)
        val useCase =
            GetGameStateUseCase(
                SpyGameRepository(GameSummary(GameStatus.WAITING, 3)),
                StubPlayerRepository(listOf(confirmed, notYet)),
            )

        val state = useCase.getState(gameId)

        assertEquals(true, state.players[0].confirmed)
        assertEquals(false, state.players[1].confirmed)
    }
}
