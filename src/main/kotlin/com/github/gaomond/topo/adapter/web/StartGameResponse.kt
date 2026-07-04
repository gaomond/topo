package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.model.StartGameResult

/**
 * POST /api/games/{id}/start のレスポンスボディ（Web 表現・US-06）。
 *
 * 開始後のゲーム ID と状態（"ACTIVE"）を返す（01-spec 1.1）。
 */
data class StartGameResponse(
    val gameId: String,
    val status: String,
) {
    companion object {
        fun from(result: StartGameResult): StartGameResponse =
            StartGameResponse(
                gameId = result.gameId.toString(),
                status = result.status.name,
            )
    }
}
