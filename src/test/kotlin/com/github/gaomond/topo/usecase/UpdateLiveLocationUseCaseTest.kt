package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.exception.GameValidationException
import com.github.gaomond.topo.domain.exception.PlayerNotFoundException
import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.model.PlayerReading
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 不正座標では更新を呼ばず、プレイヤー不在・非所属では拒否することを検証する。 */
class UpdateLiveLocationUseCaseTest {
    private data class UpdateCall(
        val gameId: UUID,
        val playerId: UUID,
        val coordinate: Coordinate,
        val at: Instant,
    )

    private class SpyPlayerRepository(
        private val updateResult: Boolean = true,
        val calls: MutableList<UpdateCall> = mutableListOf(),
    ) : PlayerRepositoryPort {
        override fun createPlayer(
            playerId: UUID,
            gameId: UUID,
            displayName: String,
        ) = error("位置更新では呼ばれない")

        override fun countByGameId(gameId: UUID): Int = error("位置更新では呼ばれない")

        override fun findByGameId(gameId: UUID): List<PlayerReading> = error("位置更新では呼ばれない")

        override fun updateLiveLocation(
            gameId: UUID,
            playerId: UUID,
            coordinate: Coordinate,
            at: Instant,
        ): Boolean {
            calls.add(UpdateCall(gameId, playerId, coordinate, at))
            return updateResult
        }
    }

    private val gameId = UUID.randomUUID()
    private val playerId = UUID.randomUUID()

    @Test
    fun test_update_withValidCoordinate_callsUpdatePortWithCoordinate() {
        val repo = SpyPlayerRepository(updateResult = true)
        UpdateLiveLocationUseCase(repo).update(UpdateLiveLocationInput(gameId, playerId, lat = 35.68, lng = 139.76))

        assertEquals(1, repo.calls.size)
        val call = repo.calls.single()
        assertEquals(gameId, call.gameId)
        assertEquals(playerId, call.playerId)
        assertEquals(Coordinate(35.68, 139.76), call.coordinate)
    }

    @Test
    fun test_update_whenPlayerNotUpdated_throwsPlayerNotFound() {
        val repo = SpyPlayerRepository(updateResult = false)
        assertThrows<PlayerNotFoundException> {
            UpdateLiveLocationUseCase(repo).update(UpdateLiveLocationInput(gameId, playerId, lat = 35.0, lng = 139.0))
        }
    }

    @Test
    fun test_update_withOutOfRangeCoordinate_throwsValidationAndSkipsPort() {
        val repo = SpyPlayerRepository()
        val ex =
            assertThrows<GameValidationException> {
                UpdateLiveLocationUseCase(repo).update(UpdateLiveLocationInput(gameId, playerId, lat = 999.0, lng = 139.0))
            }
        assertEquals(GameValidationException.Reason.INVALID_COORDINATE, ex.reason)
        assertTrue(repo.calls.isEmpty(), "不正座標では更新ポートを呼ばない")
    }

    @Test
    fun test_update_withNaN_throwsValidationAndSkipsPort() {
        val repo = SpyPlayerRepository()
        assertThrows<GameValidationException> {
            UpdateLiveLocationUseCase(repo).update(UpdateLiveLocationInput(gameId, playerId, lat = Double.NaN, lng = 139.0))
        }
        assertNull(repo.calls.getOrNull(0))
    }
}
