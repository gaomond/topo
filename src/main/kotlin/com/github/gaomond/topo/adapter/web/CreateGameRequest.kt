package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.CreateGameInput

/**
 * ゲーム作成のリクエストボディ。
 *
 * @param objectType 選択可能なオブジェクト種別。
 * @param areaPreset 面積プリセットの key。
 * @param playerCount 固定参加人数。3 以上。
 * @param displayName 任意。未送信・null・空・空白のみは表示名を補完する。
 */
data class CreateGameRequest(
    val objectType: String,
    val areaPreset: String,
    val playerCount: Int,
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
