package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.StartGameResult

/** 開始成功時の status は "ACTIVE"。 */
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
