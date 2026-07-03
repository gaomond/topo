package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.model.JoinGameResult

/**
 * POST /api/games/{id}/players のレスポンスボディ（Web 表現）。
 *
 * サーバーは playerId のみを返す。待機画面 URL の組み立てはクライアント責務。
 */
data class JoinGameResponse(
    val playerId: String,
) {
    companion object {
        fun from(result: JoinGameResult): JoinGameResponse = JoinGameResponse(playerId = result.playerId.toString())
    }
}
