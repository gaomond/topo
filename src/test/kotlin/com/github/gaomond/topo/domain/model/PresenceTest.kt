package com.github.gaomond.topo.domain.model

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 在室判定の TTL 境界ちょうど・超過と、記録時刻未送信を検証する。 */
class PresenceTest {
    private val now = Instant.parse("2026-07-06T12:00:00Z")

    @Test
    fun `test_isOnline_liveAtがnull_falseを返す`() {
        assertFalse(Presence.isOnline(liveAt = null, now = now))
    }

    @Test
    fun `test_isOnline_TTL内_trueを返す`() {
        assertTrue(Presence.isOnline(liveAt = now.minusSeconds(5), now = now))
    }

    @Test
    fun `test_isOnline_TTL境界ちょうど_trueを返す`() {
        assertTrue(Presence.isOnline(liveAt = now.minus(Presence.TTL), now = now))
    }

    @Test
    fun `test_isOnline_TTL超過_falseを返す`() {
        assertFalse(Presence.isOnline(liveAt = now.minus(Presence.TTL).minusMillis(1), now = now))
    }
}
