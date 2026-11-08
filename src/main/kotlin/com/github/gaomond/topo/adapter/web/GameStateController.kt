package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.GetGameStateInput
import com.github.gaomond.topo.usecase.GetGameStateUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class GameStateController(
    private val getGameStateUseCase: GetGameStateUseCase,
) {
    @GetMapping("/api/games/{id}")
    fun getGame(
        @PathVariable id: UUID,
    ): GameStateResponse = GameStateResponse.from(getGameStateUseCase.getState(GetGameStateInput(id)))
}
