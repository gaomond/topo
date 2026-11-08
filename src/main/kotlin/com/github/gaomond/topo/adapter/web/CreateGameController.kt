package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.CreateGameUseCase
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class CreateGameController(
    private val createGameUseCase: CreateGameUseCase,
) {
    @PostMapping("/api/games")
    @ResponseStatus(HttpStatus.CREATED)
    fun createGame(
        @RequestBody request: CreateGameRequest,
    ): CreateGameResponse {
        val result = createGameUseCase.create(request.toInput())
        return CreateGameResponse.from(result)
    }
}
