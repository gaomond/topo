package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.StartGameResult
import io.swagger.v3.oas.annotations.media.Schema

/** 開始成功時の status は "ACTIVE"。 */
@Schema(requiredProperties = ["gameId", "status"])
data class StartGameResponse(
    @field:Schema(format = "uuid")
    val gameId: String,
    @field:Schema(allowableValues = ["ACTIVE"])
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
