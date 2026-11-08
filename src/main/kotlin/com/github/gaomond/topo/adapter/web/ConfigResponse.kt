package com.github.gaomond.topo.adapter.web

data class ConfigResponse(
    val objectTypes: List<String>,
    val areaPresets: List<AreaPresetPayload>,
)

data class AreaPresetPayload(
    val key: String,
    val label: String,
    val sqm: Long,
)
