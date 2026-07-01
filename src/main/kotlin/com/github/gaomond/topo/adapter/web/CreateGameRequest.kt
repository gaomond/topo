package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.model.GameCreationCommand

/**
 * POST /api/games のリクエストボディ（Web 表現）。
 *
 * Domain 入力（[GameCreationCommand]）とは別型にし、ここで JSON ↔ ドメイン入力の境界を持つ。
 * 生の値検証（objectType 妥当性 / areaPreset 妥当性 / playerCount >= 3）は UseCase が担う。
 *
 * @param objectType  集計対象種別（例: "shrine"）。必須
 * @param areaPreset  面積プリセット key（例: "medium"）。必須
 * @param playerCount 固定参加人数。必須
 * @param displayName 作成者の表示名。任意（未送信/null ならサーバーがフォールバック）
 */
data class CreateGameRequest(
    val objectType: String,
    val areaPreset: String,
    val playerCount: Int,
    val displayName: String? = null,
) {
    fun toCommand(): GameCreationCommand =
        GameCreationCommand(
            objectType = objectType,
            areaPresetKey = areaPreset,
            playerCount = playerCount,
            displayName = displayName,
        )
}
