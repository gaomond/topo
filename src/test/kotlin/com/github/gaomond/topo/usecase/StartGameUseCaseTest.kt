package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.exception.GameNotFoundException
import com.github.gaomond.topo.domain.exception.GameStartNotAllowedException
import com.github.gaomond.topo.domain.exception.NotGameCreatorException
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

/** 作成者判定を状態・人数判定より優先し、拒否時に状態を保存しないことを検証する。 */
class StartGameUseCaseTest {
    private class SpyGameRepository(
        private val summary: GameSummary?,
        val updatedStatuses: MutableList<Pair<UUID, GameStatus>> = mutableListOf(),
    ) : GameRepositoryPort {
        override fun createGame(game: Game) = error("開始では呼ばれない")

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

        override fun findSummary(gameId: UUID): GameSummary? = error("開始ではロック付き取得を使う")

        override fun findByIdWithLock(gameId: UUID): Game? =
            summary?.let {
                Game.restore(gameId, it.status, it.playerCount, it.creatorPlayerId, ObjectType.SHRINE, it.areaThreshold)
            }
    }

    private class StubPlayerRepository(
        private val count: Int?,
    ) : PlayerRepositoryPort {
        override fun createPlayer(player: Player) = error("開始では呼ばれない")

        override fun countByGameId(gameId: UUID): Int = count ?: error("権限がなければ人数を取得しない")

        override fun findByGameId(gameId: UUID): List<PlayerReading> = error("開始では呼ばれない")

        override fun updateLiveLocation(
            gameId: UUID,
            playerId: UUID,
            coordinate: Coordinate,
            at: Instant,
        ): Boolean = error("開始では呼ばれない")
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
        val gameRepo = SpyGameRepository(GameSummary(GameStatus.WAITING, 3, creatorPlayerId = creator, areaThreshold = 2_000_000.0))
        val result = StartGameUseCase(gameRepo, StubPlayerRepository(count = 3)).start(StartGameInput(gameId, creator))

        assertEquals(GameStatus.ACTIVE, result.status)
        assertEquals(gameId, result.gameId)
        assertEquals(listOf(gameId to GameStatus.ACTIVE), gameRepo.updatedStatuses)
    }

    @Test
    fun test_start_withUnknownGameId_throwsNotFound() {
        val gameRepo = SpyGameRepository(summary = null)
        assertThrows<GameNotFoundException> {
            StartGameUseCase(gameRepo, StubPlayerRepository(0)).start(StartGameInput(gameId, creator))
        }
        assertTrue(gameRepo.updatedStatuses.isEmpty())
    }

    @Test
    fun test_start_withNonCreator_throwsNotGameCreator() {
        val gameRepo = SpyGameRepository(GameSummary(GameStatus.WAITING, 3, creatorPlayerId = creator, areaThreshold = 2_000_000.0))
        val stranger = UUID.randomUUID()
        assertThrows<NotGameCreatorException> {
            StartGameUseCase(gameRepo, StubPlayerRepository(3)).start(StartGameInput(gameId, stranger))
        }
        assertTrue(gameRepo.updatedStatuses.isEmpty())
    }

    @Test
    fun test_start_withNullCreatorPlayerId_throwsNotGameCreator() {
        // 作成者未設定なら、どのプレイヤーも開始できない。
        val gameRepo = SpyGameRepository(GameSummary(GameStatus.WAITING, 3, creatorPlayerId = null, areaThreshold = 2_000_000.0))
        assertThrows<NotGameCreatorException> {
            StartGameUseCase(gameRepo, StubPlayerRepository(3)).start(StartGameInput(gameId, creator))
        }
    }

    @Test
    fun test_start_withUnderCapacity_throwsStartNotAllowed() {
        val gameRepo = SpyGameRepository(GameSummary(GameStatus.WAITING, 3, creatorPlayerId = creator, areaThreshold = 2_000_000.0))
        assertThrows<GameStartNotAllowedException> {
            StartGameUseCase(gameRepo, StubPlayerRepository(count = 2)).start(StartGameInput(gameId, creator))
        }
        assertTrue(gameRepo.updatedStatuses.isEmpty(), "定員未達では更新しない")
    }

    @Test
    fun test_start_withActiveStatus_throwsStartNotAllowed() {
        // 開始済みのゲームに対する再実行は拒否する。
        val gameRepo = SpyGameRepository(GameSummary(GameStatus.ACTIVE, 3, creatorPlayerId = creator, areaThreshold = 2_000_000.0))
        assertThrows<GameStartNotAllowedException> {
            StartGameUseCase(gameRepo, StubPlayerRepository(3)).start(StartGameInput(gameId, creator))
        }
        assertTrue(gameRepo.updatedStatuses.isEmpty())
    }

    @Test
    fun test_start_withCompletedStatus_throwsStartNotAllowed() {
        val gameRepo = SpyGameRepository(GameSummary(GameStatus.COMPLETED, 3, creatorPlayerId = creator, areaThreshold = 2_000_000.0))
        assertThrows<GameStartNotAllowedException> {
            StartGameUseCase(gameRepo, StubPlayerRepository(3)).start(StartGameInput(gameId, creator))
        }
        assertTrue(gameRepo.updatedStatuses.isEmpty())
    }

    @Test
    fun test_start_withNonCreatorAndUnderCapacity_prefersForbiddenOverConflict() {
        // 非作成者に定員未達を漏らさず、作成者でないことを優先して拒否する。
        val gameRepo = SpyGameRepository(GameSummary(GameStatus.WAITING, 3, creatorPlayerId = creator, areaThreshold = 2_000_000.0))
        val stranger = UUID.randomUUID()
        assertThrows<NotGameCreatorException> {
            StartGameUseCase(gameRepo, StubPlayerRepository(count = 1)).start(StartGameInput(gameId, stranger))
        }
    }

    @Test
    fun test_start_withNonCreatorAndActiveStatus_prefersForbiddenOverConflict() {
        val gameRepo = SpyGameRepository(GameSummary(GameStatus.ACTIVE, 3, creatorPlayerId = creator, areaThreshold = 2_000_000.0))
        assertThrows<NotGameCreatorException> {
            StartGameUseCase(gameRepo, StubPlayerRepository(count = null)).start(StartGameInput(gameId, UUID.randomUUID()))
        }
        assertTrue(gameRepo.updatedStatuses.isEmpty())
    }

    @Test
    fun test_start_whenNotAllowed_doesNotUpdateStatus() {
        val gameRepo = SpyGameRepository(GameSummary(GameStatus.WAITING, 3, creatorPlayerId = creator, areaThreshold = 2_000_000.0))
        assertThrows<GameStartNotAllowedException> {
            StartGameUseCase(gameRepo, StubPlayerRepository(count = 2)).start(StartGameInput(gameId, creator))
        }
        assertNull(gameRepo.updatedStatuses.getOrNull(0))
    }
}
