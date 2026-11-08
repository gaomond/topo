package com.github.gaomond.topo.domain.model

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** ライブ位置未送信や未計測を null で表現できることを検証する。 */
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
    fun test_playerSnapshot_defaultsLiveNullAndOfflineWhenOmitted() {
        val snapshot = PlayerSnapshot(UUID.randomUUID(), "たろう", confirmed = false)
        assertNull(snapshot.live)
        assertEquals(false, snapshot.online)
    }
}
