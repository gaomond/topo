package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.UpdateLiveLocationUseCase
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * ライブ位置更新の inbound アダプタ。PUT /api/games/{id}/players/{pid}/location を提供する（US-07・DESIGN API #5）。
 *
 * Web 表現（[UpdateLocationRequest]）+ パス gameId / pid → UseCase → 204 No Content（ボディなし・D3 案A）。
 * ドメイン例外（座標不正→400 / 不在・非所属→404）のマッピングは [GameApiExceptionHandler] が担う。
 * 高頻度・副作用なしのため最軽量に保つ（UseCase を呼ぶだけ。adapter 間直呼びなし）。
 */
@RestController
class UpdateLocationController(
    private val updateLiveLocationUseCase: UpdateLiveLocationUseCase,
) {
    @PutMapping("/api/games/{id}/players/{pid}/location")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun updateLocation(
        @PathVariable id: UUID,
        @PathVariable pid: UUID,
        @RequestBody request: UpdateLocationRequest,
    ) {
        updateLiveLocationUseCase.update(id, pid, request.lat, request.lng)
    }
}
