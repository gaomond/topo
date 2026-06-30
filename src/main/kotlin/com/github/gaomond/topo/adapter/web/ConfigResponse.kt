package com.github.gaomond.topo.adapter.web

/**
 * GET /api/config のレスポンス DTO。Domain 型を Web 表現へ変換する境界。
 */
data class ConfigResponse(
    val objectTypes: List<String>,
    val areaPresets: List<AreaPresetPayload>,
)

/**
 * 面積プリセットの Web 表現。フィールド名は API の JSON キーに一致させる。
 */
data class AreaPresetPayload(
    val key: String,
    val label: String,
    val sqm: Long,
)
