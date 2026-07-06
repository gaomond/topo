package com.github.gaomond.topo.domain.model

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * US-08 Level 1（Domain）検証。live 位置・暫定面積・生読み取りの Domain 値が値を保持し、
 * null 許容（未送信 live / 3 点未満で currentArea なし）を表現できることを検証する。
 * テスト対象は src/ の [LiveLocation] / [CurrentArea] / [PlayerReading] / [PlayerSnapshot] / [GameState]。
 */
class GameStateModelTest {
    @Test
    fun test_liveLocation_holdsCoordinateAndInstant() {
        val at = Instant.parse("2026-07-06T12:00:00Z")
        val live = LiveLocation(Coordinate(35.68, 139.76), at)

        assertEquals(35.68, live.coordinate.lat)
        assertEquals(139.76, live.coordinate.lng)
        assertEquals(at, live.at)
    }

    @Test
    fun test_currentArea_holdsSqmAndHull() {
        val hull = listOf(Coordinate(35.68, 139.76), Coordinate(35.69, 139.77), Coordinate(35.68, 139.78))
        val area = CurrentArea(sqm = 1234.5, hull = hull)

        assertEquals(1234.5, area.sqm)
        assertEquals(3, area.hull.size)
    }

    @Test
    fun test_playerReading_allowsNullLive() {
        val reading = PlayerReading(UUID.randomUUID(), "たろう", confirmed = false, live = null)
        assertNull(reading.live)
    }

    @Test
    fun test_gameState_defaultsCurrentAreaToNull() {
        // 3 点未満などで currentArea が無い状態を null で表現できる（既定 null）。
        val state =
            GameState(
                gameId = UUID.randomUUID(),
                status = GameStatus.WAITING,
                playerCount = 3,
                creatorPlayerId = null,
                players = emptyList(),
            )
        assertNull(state.currentArea)
    }

    @Test
    fun test_playerSnapshot_defaultsLiveNullAndOfflineWhenOmitted() {
        val snapshot = PlayerSnapshot(UUID.randomUUID(), "たろう", confirmed = false)
        assertNull(snapshot.live)
        assertEquals(false, snapshot.online)
    }
}
