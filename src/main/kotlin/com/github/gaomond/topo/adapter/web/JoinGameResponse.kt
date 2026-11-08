package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.JoinGameResult

/** 待機画面 URL の組み立てはクライアントが行う。 */
data class JoinGameResponse(
    val playerId: String,
) {
    companion object {
        fun from(result: JoinGameResult): JoinGameResponse = JoinGameResponse(playerId = result.playerId.toString())
    }
}
