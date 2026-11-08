package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.CreateGameUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "ゲームの準備")
class CreateGameController(
    private val createGameUseCase: CreateGameUseCase,
) {
    @Operation(
        operationId = "createGame",
        summary = "ゲームを作成する",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "ゲームと作成者を作成した",
                content = [
                    Content(mediaType = "application/json", schema = Schema(implementation = CreateGameResponse::class)),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "形式不正、または種別・プリセット・人数が不正",
                content = [
                    Content(mediaType = "application/json", schema = Schema(implementation = RequestErrorResponse::class)),
                    Content(mediaType = "application/problem+json", schema = Schema(implementation = ValidationProblemResponse::class)),
                ],
            ),
        ],
    )
    @PostMapping("/api/games")
    @ResponseStatus(HttpStatus.CREATED)
    fun createGame(
        @RequestBody request: CreateGameRequest,
    ): CreateGameResponse {
        val result = createGameUseCase.create(request.toInput())
        return CreateGameResponse.from(result)
    }
}
