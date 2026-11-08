package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.StartGameInput
import com.github.gaomond.topo.usecase.StartGameUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@Tag(name = "ゲームの準備")
class StartGameController(
    private val startGameUseCase: StartGameUseCase,
) {
    @Operation(
        operationId = "startGame",
        summary = "ゲームを開始する",
        description = "作成者のみ、定員が揃った待機中のゲームを開始できる。",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "ゲームを開始した",
                content = [
                    Content(mediaType = "application/json", schema = Schema(implementation = StartGameResponse::class)),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "IDやリクエストの形式が不正",
                content = [
                    Content(mediaType = "application/json", schema = Schema(implementation = RequestErrorResponse::class)),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "ゲームの作成者以外による要求",
                content = [
                    Content(mediaType = "application/problem+json", schema = Schema(implementation = ApiProblemResponse::class)),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "ゲームが見つからない",
                content = [
                    Content(mediaType = "application/problem+json", schema = Schema(implementation = ApiProblemResponse::class)),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "ゲームを開始できない状態",
                content = [
                    Content(mediaType = "application/problem+json", schema = Schema(implementation = ApiProblemResponse::class)),
                ],
            ),
        ],
    )
    @PostMapping("/api/games/{id}/start")
    fun startGame(
        @PathVariable id: UUID,
        @RequestBody request: StartGameRequest,
    ): StartGameResponse {
        val result = startGameUseCase.start(StartGameInput(id, request.playerId))
        return StartGameResponse.from(result)
    }
}
