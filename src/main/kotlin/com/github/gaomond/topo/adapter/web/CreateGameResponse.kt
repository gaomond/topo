package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.CreateGameResult

/** 招待 URL・作成者 URL の組み立てはクライアントが行う。 */
data class CreateGameResponse(
    val gameId: String,
    val playerId: String,
) {
    companion object {
        fun from(result: CreateGameResult): CreateGameResponse =
            CreateGameResponse(
                gameId = result.gameId.toString(),
                playerId = result.playerId.toString(),
            )
    }
}
