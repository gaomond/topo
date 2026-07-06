package com.github.gaomond.topo.domain.model

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * US-08 Level 1（Domain）検証。presence（在室判定）の純粋ロジックを検証する。
 * `online = (now - live_at) <= TTL`（TTL=10 秒）の境界内/境界ちょうど/超過、live_at=null を検証する。
 * テスト対象は src/ の [Presence] を import（再定義しない）。
 */
class PresenceTest {
    private val now = Instant.parse("2026-07-06T12:00:00Z")

    @Test
    fun `test_isOnline_liveAtがnull_falseを返す`() {
        assertFalse(Presence.isOnline(liveAt = null, now = now))
    }

    @Test
    fun `test_isOnline_TTL内_trueを返す`() {
        // 5 秒前（TTL=10 秒の内側）。
        assertTrue(Presence.isOnline(liveAt = now.minusSeconds(5), now = now))
    }

    @Test
    fun `test_isOnline_TTL境界ちょうど_trueを返す`() {
        // now - liveAt == TTL（10 秒）は online（<=）。
        assertTrue(Presence.isOnline(liveAt = now.minus(Presence.TTL), now = now))
    }

    @Test
    fun `test_isOnline_TTL超過_falseを返す`() {
        // TTL をわずかに超える（10 秒 + 1ms）と false。
        assertFalse(Presence.isOnline(liveAt = now.minus(Presence.TTL).minusMillis(1), now = now))
    }
}
