package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.GameNotFoundException
import com.github.gaomond.topo.domain.GameStartNotAllowedException
import com.github.gaomond.topo.domain.NotGameCreatorException
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.GameSummary
import com.github.gaomond.topo.domain.model.PlayerSnapshot
import com.github.gaomond.topo.domain.port.GameRepositoryPort
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * US-06 Level 2（UseCase）検証。ポートは手書きの spy で差し替える（外部モックライブラリ非依存）。
 * 存在確認 / creator 判定（403）/ 状態・定員判定（409）/ ACTIVE 更新 / 拒否時未更新・冪等性を検証する。
 * テスト対象は src/ の [StartGameUseCase] を import（再定義しない）。
 */
class StartGameUseCaseTest {
    private class SpyGameRepository(
        private val summary: GameSummary?,
        val updatedStatuses: MutableList<Pair<UUID, GameStatus>> = mutableListOf(),
    ) : GameRepositoryPort {
        override fun createGame(
            gameId: UUID,
            status: GameStatus,
            playerCount: Int,
            objectType: String,
            areaThreshold: Double,
        ) = error("開始では呼ばれない")

        override fun updateCreatorPlayerId(
            gameId: UUID,
            creatorPlayerId: UUID,
        ) = error("開始では呼ばれない")

        override fun updateStatus(
            gameId: UUID,
            status: GameStatus,
        ) {
            updatedStatuses.add(gameId to status)
        }

        override fun findSummary(gameId: UUID): GameSummary? = summary
    }

    private class StubPlayerRepository(
        private val count: Int,
    ) : PlayerRepositoryPort {
        override fun createPlayer(
            playerId: UUID,
            gameId: UUID,
            displayName: String,
        ) = error("開始では呼ばれない")

        override fun countByGameId(gameId: UUID): Int = count

        override fun findByGameId(gameId: UUID): List<PlayerSnapshot> = error("開始では呼ばれない")
    }

    private val gameId = UUID.randomUUID()
    private val creator = UUID.randomUUID()

    private fun useCase(
        summary: GameSummary?,
        count: Int = 0,
        gameRepo: SpyGameRepository = SpyGameRepository(summary),
    ) = StartGameUseCase(gameRepo, StubPlayerRepository(count))

    @Test
    fun test_start_withCreatorAndFullWaiting_updatesStatusToActiveAndReturnsActive() {
        val gameRepo = SpyGameRepository(GameSummary(GameStatus.WAITING, 3, creatorPlayerId = creator))
        val result = StartGameUseCase(gameRepo, StubPlayerRepository(count = 3)).start(gameId, creator)

        assertEquals(GameStatus.ACTIVE, result.status)
        assertEquals(gameId, result.gameId)
        assertEquals(listOf(gameId to GameStatus.ACTIVE), gameRepo.updatedStatuses)
    }

    @Test
    fun test_start_withUnknownGameId_throwsNotFound() {
        val gameRepo = SpyGameRepository(summary = null)
        assertThrows<GameNotFoundException> {
            StartGameUseCase(gameRepo, StubPlayerRepository(0)).start(gameId, creator)
        }
        assertTrue(gameRepo.updatedStatuses.isEmpty())
    }

    @Test
    fun test_start_withNonCreator_throwsNotGameCreator() {
        val gameRepo = SpyGameRepository(GameSummary(GameStatus.WAITING, 3, creatorPlayerId = creator))
        val stranger = UUID.randomUUID()
        assertThrows<NotGameCreatorException> {
            StartGameUseCase(gameRepo, StubPlayerRepository(3)).start(gameId, stranger)
        }
        assertTrue(gameRepo.updatedStatuses.isEmpty())
    }

    @Test
    fun test_start_withNullCreatorPlayerId_throwsNotGameCreator() {
        // creator 未設定（作成直後の一瞬）は誰であれ非作成者扱い。
        val gameRepo = SpyGameRepository(GameSummary(GameStatus.WAITING, 3, creatorPlayerId = null))
        assertThrows<NotGameCreatorException> {
            StartGameUseCase(gameRepo, StubPlayerRepository(3)).start(gameId, creator)
        }
    }

    @Test
    fun test_start_withUnderCapacity_throwsStartNotAllowed() {
        val gameRepo = SpyGameRepository(GameSummary(GameStatus.WAITING, 3, creatorPlayerId = creator))
        assertThrows<GameStartNotAllowedException> {
            StartGameUseCase(gameRepo, StubPlayerRepository(count = 2)).start(gameId, creator)
        }
        assertTrue(gameRepo.updatedStatuses.isEmpty(), "定員未達では更新しない")
    }

    @Test
    fun test_start_withActiveStatus_throwsStartNotAllowed() {
        // 2 回目の開始相当（冪等性）: 既に ACTIVE なので 409。
        val gameRepo = SpyGameRepository(GameSummary(GameStatus.ACTIVE, 3, creatorPlayerId = creator))
        assertThrows<GameStartNotAllowedException> {
            StartGameUseCase(gameRepo, StubPlayerRepository(3)).start(gameId, creator)
        }
        assertTrue(gameRepo.updatedStatuses.isEmpty())
    }

    @Test
    fun test_start_withCompletedStatus_throwsStartNotAllowed() {
        val gameRepo = SpyGameRepository(GameSummary(GameStatus.COMPLETED, 3, creatorPlayerId = creator))
        assertThrows<GameStartNotAllowedException> {
            StartGameUseCase(gameRepo, StubPlayerRepository(3)).start(gameId, creator)
        }
        assertTrue(gameRepo.updatedStatuses.isEmpty())
    }

    @Test
    fun test_start_withNonCreatorAndUnderCapacity_prefersForbiddenOverConflict() {
        // 権限（403）を状態・定員（409）より先に評価する（リスク1 の確定順序）。
        val gameRepo = SpyGameRepository(GameSummary(GameStatus.WAITING, 3, creatorPlayerId = creator))
        val stranger = UUID.randomUUID()
        assertThrows<NotGameCreatorException> {
            StartGameUseCase(gameRepo, StubPlayerRepository(count = 1)).start(gameId, stranger)
        }
    }

    @Test
    fun test_start_whenNotAllowed_doesNotUpdateStatus() {
        val gameRepo = SpyGameRepository(GameSummary(GameStatus.WAITING, 3, creatorPlayerId = creator))
        assertThrows<GameStartNotAllowedException> {
            StartGameUseCase(gameRepo, StubPlayerRepository(count = 2)).start(gameId, creator)
        }
        assertNull(gameRepo.updatedStatuses.getOrNull(0))
    }
}
