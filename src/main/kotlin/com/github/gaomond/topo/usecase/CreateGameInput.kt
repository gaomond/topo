package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.model.AreaPreset
import com.github.gaomond.topo.domain.model.ObjectType
import java.util.UUID

/**
 * 未検証の作成入力。種別は [ObjectType.selectableFromJsonValueOrNull]、
 * 面積プリセットは [AreaPreset.byKey] で解決する。
 *
 * @param displayName null・空・空白のみはフォールバック対象。
 */
data class CreateGameInput(
    val objectType: String,
    val areaPresetKey: String,
    val playerCount: Int,
    val displayName: String?,
)

/** 作成したゲームと作成者の ID。 */
data class CreateGameResult(
    val gameId: UUID,
    val playerId: UUID,
)
