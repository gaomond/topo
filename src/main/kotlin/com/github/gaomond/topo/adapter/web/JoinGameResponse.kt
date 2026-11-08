package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.JoinGameResult
import io.swagger.v3.oas.annotations.media.Schema

/** 待機画面 URL の組み立てはクライアントが行う。 */
@Schema(requiredProperties = ["playerId"])
data class JoinGameResponse(
    @field:Schema(format = "uuid")
    val playerId: String,
) {
    companion object {
        fun from(result: JoinGameResult): JoinGameResponse = JoinGameResponse(playerId = result.playerId.toString())
    }
}
