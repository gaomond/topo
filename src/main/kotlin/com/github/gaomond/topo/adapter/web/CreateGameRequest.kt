package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.CreateGameInput
import io.swagger.v3.oas.annotations.media.Schema

/**
 * ゲーム作成のリクエストボディ。
 *
 * @param objectType 選択可能なオブジェクト種別。
 * @param areaPreset 面積プリセットの key。
 * @param playerCount 固定参加人数。3 以上。
 * @param displayName 任意。未送信・null・空・空白のみは表示名を補完する。
 */
data class CreateGameRequest(
    @field:Schema(allowableValues = ["shrine"], requiredMode = Schema.RequiredMode.REQUIRED)
    val objectType: String,
    @field:Schema(allowableValues = ["small", "medium", "large"], requiredMode = Schema.RequiredMode.REQUIRED)
    val areaPreset: String,
    @field:Schema(minimum = "3", description = "固定参加人数", requiredMode = Schema.RequiredMode.REQUIRED)
    val playerCount: Int,
    @field:Schema(description = "省略・null・空・空白のみの場合はプレイヤーIDの先頭8文字で補完する。")
    val displayName: String? = null,
) {
    fun toInput(): CreateGameInput =
        CreateGameInput(
            objectType = objectType,
            areaPresetKey = areaPreset,
            playerCount = playerCount,
            displayName = displayName,
        )
}
