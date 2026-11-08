package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.model.CurrentArea
import com.github.gaomond.topo.domain.model.LiveLocation
import com.github.gaomond.topo.domain.model.PlayerSnapshot
import com.github.gaomond.topo.usecase.GetGameStateResult
import io.swagger.v3.oas.annotations.media.Schema

/** ゲーム状態のレスポンス。確定結果 result は現在常に null を返す。 */
@Schema(requiredProperties = ["gameId", "status", "playerCount", "creatorPlayerId", "players", "areaThreshold", "currentArea", "result"])
data class GameStateResponse(
    @field:Schema(format = "uuid")
    val gameId: String,
    @field:Schema(allowableValues = ["WAITING", "ACTIVE", "COMPLETED"])
    val status: String,
    val playerCount: Int,
    // 作成者未設定なら null。
    @field:Schema(format = "uuid")
    val creatorPlayerId: String?,
    val players: List<PlayerPayload>,
    // 作成時に確定する面積閾値（m²）。
    @field:Schema(description = "作成時に確定する面積閾値（m²）")
    val areaThreshold: Double,
    // ACTIVE 以外、ライブ座標 3 点未満、計算結果が空の場合は null。
    val currentArea: CurrentAreaPayload?,
    @field:Schema(types = ["null"], description = "現在は常にnull。確定結果の形式は未実装。")
    val result: Any? = null,
) {
    companion object {
        fun from(state: GetGameStateResult): GameStateResponse =
            GameStateResponse(
                gameId = state.gameId.toString(),
                status = state.status.name,
                playerCount = state.playerCount,
                creatorPlayerId = state.creatorPlayerId?.toString(),
                players = state.players.map(PlayerPayload::from),
                areaThreshold = state.areaThreshold,
                currentArea = state.currentArea?.let(CurrentAreaPayload::from),
            )
    }
}

@Schema(requiredProperties = ["playerId", "displayName", "confirmed", "live", "online"])
data class PlayerPayload(
    @field:Schema(format = "uuid")
    val playerId: String,
    val displayName: String,
    val confirmed: Boolean,
    // 未送信、または座標・記録時刻が欠けている場合は null。
    val live: LiveLocationPayload?,
    // 最新ライブ位置の記録時刻から 10 秒以内なら在室。
    @field:Schema(description = "ライブ位置の記録から10秒以内ならtrue。")
    val online: Boolean,
) {
    companion object {
        fun from(snapshot: PlayerSnapshot): PlayerPayload =
            PlayerPayload(
                playerId = snapshot.playerId.toString(),
                displayName = snapshot.displayName,
                confirmed = snapshot.confirmed,
                live = snapshot.live?.let(LiveLocationPayload::from),
                online = snapshot.online,
            )
    }
}

/** ライブ位置の記録時刻 at は ISO-8601 の UTC 表記（末尾 Z）。 */
@Schema(requiredProperties = ["lat", "lng", "at"])
data class LiveLocationPayload(
    @field:Schema(minimum = "-90", maximum = "90", description = "WGS84の緯度")
    val lat: Double,
    @field:Schema(minimum = "-180", maximum = "180", description = "WGS84の経度")
    val lng: Double,
    @field:Schema(format = "date-time", description = "ISO-8601のUTC表記（末尾Z）", example = "2026-11-08T09:00:00Z")
    val at: String,
) {
    companion object {
        fun from(live: LiveLocation): LiveLocationPayload =
            LiveLocationPayload(
                lat = live.coordinate.lat,
                lng = live.coordinate.lng,
                at = live.at.toString(),
            )
    }
}

/**
 * 暫定凸包の測地面積（sqm、m²）と頂点列。
 *
 * [hull] は `[[lat, lng], ...]` の順で返す。多角形では閉環し、
 * 退化時は線分または点の頂点列となる。
 */
@Schema(requiredProperties = ["sqm", "hull"])
data class CurrentAreaPayload(
    @field:Schema(description = "暫定凸包の測地面積（m²）。退化時は0。")
    val sqm: Double,
    @field:Schema(description = "頂点列は [[lat, lng], ...]。多角形では閉環し、退化時は点または線分となる。")
    val hull: List<List<Double>>,
) {
    companion object {
        fun from(area: CurrentArea): CurrentAreaPayload =
            CurrentAreaPayload(
                sqm = area.sqm,
                hull = area.hull.map { listOf(it.lat, it.lng) },
            )
    }
}
