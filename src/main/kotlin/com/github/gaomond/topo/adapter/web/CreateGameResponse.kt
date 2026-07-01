package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.model.GameCreationResult

/**
 * POST /api/games のレスポンスボディ（Web 表現）。
 *
 * サーバーは gameId / playerId のみを返す。招待 URL / 作成者 URL の組み立てはクライアント責務。
 */
data class CreateGameResponse(
    val gameId: String,
    val playerId: String,
) {
    companion object {
        fun from(result: GameCreationResult): CreateGameResponse =
            CreateGameResponse(
                gameId = result.gameId.toString(),
                playerId = result.playerId.toString(),
            )
    }
}
