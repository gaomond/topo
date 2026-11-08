package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.model.AreaPreset
import com.github.gaomond.topo.domain.model.ObjectType
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "ゲームの準備")
class ConfigController {
    @Operation(
        operationId = "getConfig",
        summary = "ゲーム設定を取得する",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "設定一覧",
                content = [
                    Content(mediaType = "application/json", schema = Schema(implementation = ConfigResponse::class)),
                ],
            ),
        ],
    )
    @GetMapping("/api/config")
    fun getConfig(): ConfigResponse =
        ConfigResponse(
            objectTypes = ObjectType.SELECTABLE.map { it.jsonValue },
            areaPresets =
                AreaPreset.ALL.map { preset ->
                    AreaPresetPayload(key = preset.key, label = preset.label, sqm = preset.sqm)
                },
        )
}
