package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.UpdateLiveLocationInput
import com.github.gaomond.topo.usecase.UpdateLiveLocationUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** 成功時は 204 No Content（ボディなし）。 */
@RestController
@Tag(name = "プレイ中")
class UpdateLocationController(
    private val updateLiveLocationUseCase: UpdateLiveLocationUseCase,
) {
    @Operation(
        operationId = "updateLocation",
        summary = "ライブ位置を更新する",
        description = "ゲーム状態による制限はなく、確定位置や集計結果には影響しない。",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204",
                description = "更新した（ボディなし）",
                content = [],
            ),
            ApiResponse(
                responseCode = "400",
                description = "IDやリクエストの形式が不正、または座標が不正",
                content = [
                    Content(mediaType = "application/json", schema = Schema(implementation = RequestErrorResponse::class)),
                    Content(mediaType = "application/problem+json", schema = Schema(implementation = ValidationProblemResponse::class)),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "ゲームまたはプレイヤーが見つからない、あるいは非所属",
                content = [
                    Content(mediaType = "application/problem+json", schema = Schema(implementation = ApiProblemResponse::class)),
                ],
            ),
        ],
    )
    @PutMapping("/api/games/{id}/players/{pid}/location")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun updateLocation(
        @PathVariable id: UUID,
        @PathVariable pid: UUID,
        @RequestBody request: UpdateLocationRequest,
    ) {
        updateLiveLocationUseCase.update(UpdateLiveLocationInput(id, pid, request.lat, request.lng))
    }
}
