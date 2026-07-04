package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.StartGameUseCase
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * ゲーム開始の inbound アダプタ。POST /api/games/{id}/start を提供する（US-06）。
 *
 * Web 表現（[StartGameRequest]）+ パス gameId → UseCase → 200 + [StartGameResponse]。
 * ドメイン例外（不在→404 / 非作成者→403 / 開始不可→409）のマッピングは
 * [GameApiExceptionHandler] が担う。
 */
@RestController
class StartGameController(
    private val startGameUseCase: StartGameUseCase,
) {
    @PostMapping("/api/games/{id}/start")
    fun startGame(
        @PathVariable id: UUID,
        @RequestBody request: StartGameRequest,
    ): StartGameResponse {
        val result = startGameUseCase.start(id, request.playerId)
        return StartGameResponse.from(result)
    }
}
