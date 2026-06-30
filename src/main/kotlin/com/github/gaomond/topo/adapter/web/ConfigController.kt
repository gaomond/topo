package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.model.AreaPreset
import com.github.gaomond.topo.domain.model.ObjectType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * ゲーム設定（選択可能なオブジェクト種別・面積プリセット）を返す inbound アダプタ。
 *
 * 静的な Domain 定数を DTO に詰めて返すだけのため UseCase 層は経由しない。
 * Domain 型から Web 表現（JSON 値）への変換のみをここで行う。
 */
@RestController
class ConfigController {
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
