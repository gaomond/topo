package com.github.gaomond.topo.adapter.web

import io.swagger.v3.oas.annotations.media.Schema

@Schema(requiredProperties = ["objectTypes", "areaPresets"])
data class ConfigResponse(
    val objectTypes: List<String>,
    val areaPresets: List<AreaPresetPayload>,
)

@Schema(requiredProperties = ["key", "label", "sqm"])
data class AreaPresetPayload(
    val key: String,
    val label: String,
    @field:Schema(description = "面積閾値（m²）")
    val sqm: Long,
)
