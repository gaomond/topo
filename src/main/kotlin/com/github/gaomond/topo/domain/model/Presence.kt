package com.github.gaomond.topo.domain.model

import java.time.Duration
import java.time.Instant

/**
 * 在室判定（presence）の純粋ロジック（US-08 / E3・E4）。
 *
 * `online = (now - live_at) <= TTL`。専用のハートビート／退出エンドポイントは設けず、
 * US-07 が更新する `live_at`（最新ライブ位置の記録時刻）を last-seen として流用する。
 *
 * [TTL] はアプリ定数（送信 2 秒間隔の約 5 倍）。数回の欠落を許容してちらつきを防ぐ。
 * プレイテストでの調整余地を残すため定数として切り出す。ロギング / Spring を import しない純 Kotlin。
 */
object Presence {
    /** 在室とみなす live_at の鮮度上限（10 秒）。 */
    val TTL: Duration = Duration.ofSeconds(10)

    /**
     * live_at の鮮度から在室（online）かを判定する。
     *
     * - [liveAt] が null（未送信）は false。
     * - `now - liveAt == TTL`（境界ちょうど）は online（`<=`）。
     * - `TTL` を超過したら false。
     *
     * @param liveAt 最新ライブ位置の記録時刻（未送信は null）
     * @param now    判定基準時刻（UseCase が Clock から確定して渡す）
     */
    fun isOnline(
        liveAt: Instant?,
        now: Instant,
    ): Boolean = liveAt != null && Duration.between(liveAt, now) <= TTL
}
