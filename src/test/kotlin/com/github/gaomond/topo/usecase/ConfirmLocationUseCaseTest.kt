package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.exception.GameConfirmationNotAllowedException
import com.github.gaomond.topo.domain.exception.GameNotFoundException
import com.github.gaomond.topo.domain.exception.InvalidCoordinateException
import com.github.gaomond.topo.domain.exception.PlayerAlreadyConfirmedException
import com.github.gaomond.topo.domain.exception.PlayerNotFoundException
import com.github.gaomond.topo.domain.model.ConfirmedLocation
import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.model.Game
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.ObjectType
import com.github.gaomond.topo.domain.model.Player
import com.github.gaomond.topo.domain.port.GameRepositoryPort
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ConfirmLocationUseCaseTest {
    private val gameId = UUID.randomUUID()
    private val playerId = UUID.randomUUID()
    private val at = Instant.parse("2026-11-08T01:00:00Z")
    private val coordinate = Coordinate(35.68, 139.76)
    private val input = ConfirmLocationInput(gameId, playerId, coordinate)
    private val gameRepository = mock<GameRepositoryPort>()
    private val playerRepository = mock<PlayerRepositoryPort>()
    private val area = mock<com.github.gaomond.topo.domain.port.HullServicePort>()
    private val useCase = ConfirmLocationUseCase(gameRepository, playerRepository, Clock.fixed(at, ZoneOffset.UTC), area)

    private fun game(status: GameStatus = GameStatus.ACTIVE) = Game.restore(gameId, status, 3, playerId, ObjectType.SHRINE, 2_000_000.0)

    private fun player(location: ConfirmedLocation? = null) = Player.restore(playerId, gameId, "探索者", location)

    private fun ready() {
        whenever(
            area.calculateArea(gameId, coordinate),
        ).thenReturn(
            com.github.gaomond.topo.domain.model
                .ConfirmationArea(1, 0.0, listOf(coordinate)),
        )
        whenever(gameRepository.findByIdWithLock(gameId)).thenReturn(game())
        whenever(playerRepository.findByGameIdAndPlayerId(gameId, playerId)).thenReturn(player())
    }

    @Test fun test_confirm_withActiveMember_savesSamePointAndTime() {
        ready()
        assertEquals(ConfirmedLocationResult(playerId, true, at), useCase.confirm(input))
        val order = inOrder(gameRepository, playerRepository)
        order.verify(gameRepository).findByIdWithLock(gameId)
        order.verify(playerRepository).findByGameIdAndPlayerId(gameId, playerId)
        order.verify(playerRepository).confirmLocation(gameId, playerId, ConfirmedLocation(coordinate, at))
        verify(gameRepository, never()).updateStatus(any())
    }

    @Test fun test_confirm_withMissingGame_throwsNotFoundBeforePlayerRead() {
        assertThrows<GameNotFoundException> { useCase.confirm(input) }
        verifyNoInteractions(playerRepository)
    }

    @Test fun test_confirm_withNonMemberAndWaiting_prefersNotFound() {
        whenever(gameRepository.findByIdWithLock(gameId)).thenReturn(game(GameStatus.WAITING))
        assertThrows<PlayerNotFoundException> { useCase.confirm(input) }
        verify(playerRepository, never()).confirmLocation(any(), any(), any())
    }

    @Test fun test_confirm_withNonActive_rejectsWithoutSave() {
        ready()
        for (status in listOf(GameStatus.WAITING, GameStatus.COMPLETED)) {
            whenever(gameRepository.findByIdWithLock(gameId)).thenReturn(game(status))
            assertThrows<GameConfirmationNotAllowedException> { useCase.confirm(input) }
        }
        verify(playerRepository, never()).confirmLocation(any(), any(), any())
    }

    @Test fun test_confirm_withConfirmedPlayer_rejectsWithoutSave() {
        ready()
        whenever(playerRepository.findByGameIdAndPlayerId(gameId, playerId)).thenReturn(player(ConfirmedLocation(coordinate, at)))
        assertThrows<PlayerAlreadyConfirmedException> { useCase.confirm(input) }
        verify(playerRepository, never()).confirmLocation(any(), any(), any())
    }

    @Test fun test_confirm_withPersistenceFailure_propagatesWithoutReReading() {
        ready()
        val failure = IllegalStateException("保存対象が存在しません")
        doThrow(failure).whenever(playerRepository).confirmLocation(any(), any(), any())
        assertSame(failure, assertThrows<IllegalStateException> { useCase.confirm(input) })
        verify(gameRepository, times(1)).findByIdWithLock(gameId)
        verify(playerRepository, times(1)).findByGameIdAndPlayerId(gameId, playerId)
    }

    @Test fun test_confirm_withPersistenceCoordinateFailure_propagatesSameException() {
        ready()
        val failure = InvalidCoordinateException("保存された座標が不正")
        doThrow(failure).whenever(playerRepository).confirmLocation(any(), any(), any())
        assertSame(failure, assertThrows<InvalidCoordinateException> { useCase.confirm(input) })
    }

    @Test fun test_confirm_finalCandidate_calculatesOnceAndUsesSameHullForCountAndSave() {
        ready()
        val hull = listOf(coordinate, Coordinate(35.68, 139.761), Coordinate(35.681, 139.76), coordinate)
        val candidate =
            com.github.gaomond.topo.domain.model
                .ConfirmationArea(3, 50.0, hull)
        whenever(area.calculateArea(gameId, coordinate)).thenReturn(candidate)
        whenever(area.countCoveredObjects(ObjectType.SHRINE, hull)).thenReturn(4)
        val result = useCase.confirm(input)
        assertEquals(50.0, result.result?.areaSqm)
        assertSame(hull, result.result?.polygon)
        verify(area, times(1)).calculateArea(gameId, coordinate)
        val saved = org.mockito.kotlin.argumentCaptor<Game>()
        verify(gameRepository).saveResult(saved.capture())
        assertSame(hull, saved.firstValue.result?.polygon)
        val order = inOrder(area, playerRepository, gameRepository)
        order.verify(area).calculateArea(gameId, coordinate)
        order.verify(playerRepository).confirmLocation(gameId, playerId, ConfirmedLocation(coordinate, at))
        order.verify(area).countCoveredObjects(ObjectType.SHRINE, hull)
        order.verify(gameRepository).saveResult(any())
    }

    @Test fun test_confirm_rejectedArea_doesNotWriteOrCount() {
        ready()
        whenever(
            area.calculateArea(gameId, coordinate),
        ).thenReturn(
            com.github.gaomond.topo.domain.model
                .ConfirmationArea(3, 0.0, listOf(coordinate)),
        )
        assertThrows<com.github.gaomond.topo.domain.exception.ConfirmationAreaRejectedException> { useCase.confirm(input) }
        verify(playerRepository, never()).confirmLocation(any(), any(), any())
        verify(area, never()).countCoveredObjects(any(), any())
        verify(gameRepository, never()).saveResult(any())
    }
}
