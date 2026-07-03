package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.JoinGameUseCase
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * ゲーム参加の inbound アダプタ。POST /api/games/{id}/players を提供する。
 *
 * Web 表現（[JoinGameRequest]）+ パス gameId → ドメイン入力 → UseCase → 201 + [JoinGameResponse]。
 * ドメイン例外（不在→404 / 参加不可→409）のマッピングは [GameApiExceptionHandler] が担う。
 */
@RestController
class JoinGameController(
    private val joinGameUseCase: JoinGameUseCase,
) {
    @PostMapping("/api/games/{id}/players")
    @ResponseStatus(HttpStatus.CREATED)
    fun joinGame(
        @PathVariable id: UUID,
        @RequestBody(required = false) request: JoinGameRequest?,
    ): JoinGameResponse {
        val command = (request ?: JoinGameRequest()).toCommand(id)
        val result = joinGameUseCase.join(command)
        return JoinGameResponse.from(result)
    }
}
