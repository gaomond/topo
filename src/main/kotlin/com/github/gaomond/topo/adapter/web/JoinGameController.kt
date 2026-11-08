package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.JoinGameUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** リクエストボディを省略した場合も、表示名未指定として参加を受け付ける。 */
@RestController
@Tag(name = "ゲームの準備")
class JoinGameController(
    private val joinGameUseCase: JoinGameUseCase,
) {
    @Operation(
        operationId = "joinGame",
        summary = "ゲームに参加する",
        description = "表示名もボディ全体も省略可能。未指定の表示名は補完する。",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "参加した",
                content = [
                    Content(mediaType = "application/json", schema = Schema(implementation = JoinGameResponse::class)),
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
                responseCode = "404",
                description = "ゲームが見つからない",
                content = [
                    Content(mediaType = "application/problem+json", schema = Schema(implementation = ApiProblemResponse::class)),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "満員、または参加できない状態",
                content = [
                    Content(mediaType = "application/problem+json", schema = Schema(implementation = ApiProblemResponse::class)),
                ],
            ),
        ],
    )
    @PostMapping("/api/games/{id}/players")
    @ResponseStatus(HttpStatus.CREATED)
    fun joinGame(
        @PathVariable id: UUID,
        @RequestBody(required = false) request: JoinGameRequest?,
    ): JoinGameResponse {
        val input = (request ?: JoinGameRequest()).toInput(id)
        val result = joinGameUseCase.join(input)
        return JoinGameResponse.from(result)
    }
}
