package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.exception.InvalidCoordinateException
import com.github.gaomond.topo.domain.exception.PlayerNotFoundException
import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.model.Player
import com.github.gaomond.topo.domain.model.PlayerReading
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** 座標の受け渡し、不在・非所属の拒否と後段例外の伝播を検証する。 */
class UpdateLiveLocationUseCaseTest {
    private data class UpdateCall(
        val gameId: UUID,
        val playerId: UUID,
        val coordinate: Coordinate,
        val at: Instant,
    )

    private class SpyPlayerRepository(
        private val updateResult: Boolean = true,
        private val failure: RuntimeException? = null,
        val calls: MutableList<UpdateCall> = mutableListOf(),
    ) : PlayerRepositoryPort {
        override fun createPlayer(player: Player) = error("位置更新では呼ばれない")

        override fun countByGameId(gameId: UUID): Int = error("位置更新では呼ばれない")

        override fun findByGameId(gameId: UUID): List<PlayerReading> = error("位置更新では呼ばれない")

        override fun updateLiveLocation(
            gameId: UUID,
            playerId: UUID,
            coordinate: Coordinate,
            at: Instant,
        ): Boolean {
            calls.add(UpdateCall(gameId, playerId, coordinate, at))
            failure?.let { throw it }
            return updateResult
        }
    }

    private val gameId = UUID.randomUUID()
    private val playerId = UUID.randomUUID()

    @Test
    fun test_update_withValidCoordinate_callsUpdatePortWithCoordinate() {
        val repo = SpyPlayerRepository(updateResult = true)
        val coordinate = Coordinate(35.68, 139.76)
        UpdateLiveLocationUseCase(repo).update(UpdateLiveLocationInput(gameId, playerId, coordinate))

        assertEquals(1, repo.calls.size)
        val call = repo.calls.single()
        assertEquals(gameId, call.gameId)
        assertEquals(playerId, call.playerId)
        assertSame(coordinate, call.coordinate)
    }

    @Test
    fun test_update_whenPlayerNotUpdated_throwsPlayerNotFound() {
        val repo = SpyPlayerRepository(updateResult = false)
        assertThrows<PlayerNotFoundException> {
            UpdateLiveLocationUseCase(repo).update(UpdateLiveLocationInput(gameId, playerId, Coordinate(35.0, 139.0)))
        }
    }

    @Test
    fun test_update_whenPortThrowsCoordinateException_propagatesSameException() {
        val failure = InvalidCoordinateException("復元した座標が不正です")
        val repo = SpyPlayerRepository(failure = failure)
        val ex =
            assertThrows<InvalidCoordinateException> {
                UpdateLiveLocationUseCase(repo).update(UpdateLiveLocationInput(gameId, playerId, Coordinate(35.0, 139.0)))
            }
        assertSame(failure, ex)
        assertEquals(1, repo.calls.size)
    }
}
