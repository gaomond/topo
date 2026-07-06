package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.model.CurrentArea
import com.github.gaomond.topo.domain.model.GameState
import com.github.gaomond.topo.domain.model.LiveLocation
import com.github.gaomond.topo.domain.model.PlayerSnapshot

/**
 * GET /api/games/{id} のレスポンスボディ（Web 表現）。
 *
 * US-05 の status / playerCount / players に加え、US-08 で各 player の live / online と、
 * 進行中メーター用の [currentArea]（live 凸包の面積・頂点列）を返す。`result`（確定結果）は
 * confirm/集計（US-11/13）まで常に null だが、前方互換のためフィールドを用意して null を返す。
 * 命名は CLAUDE.md 規約（body 全体 = XxxResponse、構成要素 = XxxPayload）に従う。
 */
data class GameStateResponse(
    val gameId: String,
    val status: String,
    val playerCount: Int,
    // 作成者の playerId（フロントの開始ボタン creator 判定用・US-06）。作成直後の一瞬は null。
    val creatorPlayerId: String?,
    val players: List<PlayerPayload>,
    // live 位置の暫定凸包（ACTIVE かつ live 点 3 点以上のときのみ非 null・US-08）。
    val currentArea: CurrentAreaPayload?,
    // 確定結果。confirm/集計（US-11/13）まで常に null。前方互換のためフィールドは用意する。
    val result: Any? = null,
) {
    companion object {
        fun from(state: GameState): GameStateResponse =
            GameStateResponse(
                gameId = state.gameId.toString(),
                status = state.status.name,
                playerCount = state.playerCount,
                creatorPlayerId = state.creatorPlayerId?.toString(),
                players = state.players.map(PlayerPayload::from),
                currentArea = state.currentArea?.let(CurrentAreaPayload::from),
            )
    }
}

/**
 * 参加者（GameStateResponse の構成要素）。
 */
data class PlayerPayload(
    val playerId: String,
    val displayName: String,
    val confirmed: Boolean,
    // 最新ライブ位置（未送信は null・US-08）。
    val live: LiveLocationPayload?,
    // live_at の鮮度（TTL 内）による在室判定（サーバー計算・US-08）。
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

/**
 * ライブ位置（PlayerPayload の構成要素・US-08）。`at` は ISO-8601 / Z（`Instant.toString()`）。
 */
data class LiveLocationPayload(
    val lat: Double,
    val lng: Double,
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
 * 進行中メーター（GameStateResponse の構成要素・US-08）。
 *
 * [hull] は凸包頂点列を `[[lat, lng], ...]` の閉環で返す。クライアントは US-09/10 でこれをそのまま描画する
 * （凸包・面積はサーバー計算・クライアント非計算）。
 */
data class CurrentAreaPayload(
    val sqm: Double,
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
