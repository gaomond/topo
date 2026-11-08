package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.StartGameInput
import com.github.gaomond.topo.usecase.StartGameUseCase
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class StartGameController(
    private val startGameUseCase: StartGameUseCase,
) {
    @PostMapping("/api/games/{id}/start")
    fun startGame(
        @PathVariable id: UUID,
        @RequestBody request: StartGameRequest,
    ): StartGameResponse {
        val result = startGameUseCase.start(StartGameInput(id, request.playerId))
        return StartGameResponse.from(result)
    }
}
