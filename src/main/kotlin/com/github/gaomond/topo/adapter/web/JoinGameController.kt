package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.JoinGameUseCase
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** リクエストボディを省略した場合も、表示名未指定として参加を受け付ける。 */
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
        val input = (request ?: JoinGameRequest()).toInput(id)
        val result = joinGameUseCase.join(input)
        return JoinGameResponse.from(result)
    }
}
