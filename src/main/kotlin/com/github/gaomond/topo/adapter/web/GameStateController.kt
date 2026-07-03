package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.GetGameStateUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * ゲーム状態取得の inbound アダプタ。GET /api/games/{id} を提供する。
 *
 * UseCase → 200 + [GameStateResponse]。不在は [GameApiExceptionHandler] が 404 にマッピングする。
 * ポーリング（フロント SWR）と復帰（01-spec 1.3）が依存する。
 */
@RestController
class GameStateController(
    private val getGameStateUseCase: GetGameStateUseCase,
) {
    @GetMapping("/api/games/{id}")
    fun getGame(
        @PathVariable id: UUID,
    ): GameStateResponse = GameStateResponse.from(getGameStateUseCase.getState(id))
}
