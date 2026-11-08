package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.model.GameStatus
import java.util.UUID

/** 開始成功時の状態。status は [GameStatus.ACTIVE]。 */
data class StartGameResult(
    val gameId: UUID,
    val status: GameStatus,
)
