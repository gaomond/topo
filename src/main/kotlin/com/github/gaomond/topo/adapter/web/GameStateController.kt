package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.GetGameStateInput
import com.github.gaomond.topo.usecase.GetGameStateUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@Tag(name = "プレイ中")
class GameStateController(
    private val getGameStateUseCase: GetGameStateUseCase,
) {
    @Operation(
        operationId = "getGame",
        summary = "ゲームの状態を取得する",
        description = "ライブ位置、在室判定、進行中の暫定面積を含む。確定結果は現在常にnull。",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "現在のゲーム状態",
                content = [
                    Content(mediaType = "application/json", schema = Schema(implementation = GameStateResponse::class)),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "IDの形式が不正",
                content = [
                    Content(mediaType = "application/json", schema = Schema(implementation = RequestErrorResponse::class)),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "ゲームが見つからない",
                content = [
                    Content(mediaType = "application/problem+json", schema = Schema(implementation = ApiProblemResponse::class)),
                ],
            ),
        ],
    )
    @GetMapping("/api/games/{id}")
    fun getGame(
        @PathVariable id: UUID,
    ): GameStateResponse = GameStateResponse.from(getGameStateUseCase.getState(GetGameStateInput(id)))
}
