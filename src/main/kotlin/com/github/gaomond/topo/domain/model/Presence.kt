package com.github.gaomond.topo.domain.model

import java.time.Duration
import java.time.Instant

/**
 * ライブ位置の記録時刻を最終確認時刻として使う在室判定。
 *
 * [TTL] は送信間隔 2 秒の約 5 倍とし、数回の送信欠落による表示のちらつきを防ぐ。
 */
object Presence {
    val TTL: Duration = Duration.ofSeconds(10)

    /**
     * 未送信（liveAt=null）は不在とし、記録時刻から [TTL] ちょうどまでは在室とみなす。
     *
     * @param now 判定対象の全員に共通の基準時刻。
     */
    fun isOnline(
        liveAt: Instant?,
        now: Instant,
    ): Boolean = liveAt != null && Duration.between(liveAt, now) <= TTL
}
