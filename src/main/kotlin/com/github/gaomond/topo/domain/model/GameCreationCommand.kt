package com.github.gaomond.topo.domain.model

/**
 * ゲーム作成のドメイン入力（不変値）。
 *
 * Web 表現（`CreateGameRequest`）とは別型にし、UseCase が受け取るドメイン入力を表す。
 * ここでは生の入力値（未検証）を保持し、検証・解決（objectType 妥当性 / areaPreset→sqm）は
 * UseCase が [ObjectType.selectableFromJsonValueOrNull] / [AreaPreset.byKey] を用いて行う。
 *
 * Spring / JPA に依存しない純粋 Kotlin 型。
 *
 * @param objectType     集計対象種別の生文字列（jsonValue。例: "shrine"）
 * @param areaPresetKey  面積プリセットの生 key（例: "medium"）
 * @param playerCount    固定参加人数（3 以上が有効）
 * @param displayName    作成者の表示名（任意。null / 空 / 空白のみはフォールバック対象）
 */
data class GameCreationCommand(
    val objectType: String,
    val areaPresetKey: String,
    val playerCount: Int,
    val displayName: String?,
)
