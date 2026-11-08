package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.model.AreaPreset
import com.github.gaomond.topo.domain.model.ObjectType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

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
