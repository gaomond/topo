package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.model.GameState

/**
 * GET /api/games/{id} のレスポンスボディ（Web 表現・最小形）。
 *
 * US-05 スコープでは status / playerCount / players（構成要素 [PlayerPayload]）のみを返す。
 * live 座標・currentArea・result は US-07〜10 で拡張する領域のため含めない（キー追加で拡張可能）。
 * 命名は CLAUDE.md 規約（body 全体 = XxxResponse、構成要素 = XxxPayload）に従う。
 */
data class GameStateResponse(
    val gameId: String,
    val status: String,
    val playerCount: Int,
    val players: List<PlayerPayload>,
) {
    companion object {
        fun from(state: GameState): GameStateResponse =
            GameStateResponse(
                gameId = state.gameId.toString(),
                status = state.status.name,
                playerCount = state.playerCount,
                players =
                    state.players.map {
                        PlayerPayload(
                            playerId = it.playerId.toString(),
                            displayName = it.displayName,
                            confirmed = it.confirmed,
                        )
                    },
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
)
