package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.CreateGameUseCase
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * ゲーム作成の inbound アダプタ。POST /api/games を提供する。
 *
 * Web 表現（[CreateGameRequest]）→ ドメイン入力 → UseCase 呼び出し → 201 + [CreateGameResponse]。
 * ドメイン例外（`GameValidationException`）の 400 マッピングは [GameApiExceptionHandler] が担う。
 */
@RestController
class CreateGameController(
    private val createGameUseCase: CreateGameUseCase,
) {
    @PostMapping("/api/games")
    @ResponseStatus(HttpStatus.CREATED)
    fun createGame(
        @RequestBody request: CreateGameRequest,
    ): CreateGameResponse {
        val result = createGameUseCase.create(request.toCommand())
        return CreateGameResponse.from(result)
    }
}
