package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.CreateGameResult
import io.swagger.v3.oas.annotations.media.Schema

/** 招待 URL・作成者 URL の組み立てはクライアントが行う。 */
@Schema(requiredProperties = ["gameId", "playerId"])
data class CreateGameResponse(
    @field:Schema(format = "uuid")
    val gameId: String,
    @field:Schema(format = "uuid")
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
