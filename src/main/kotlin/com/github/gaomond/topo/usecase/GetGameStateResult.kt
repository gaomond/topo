package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.model.CurrentArea
import com.github.gaomond.topo.domain.model.GameResult
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.PlayerSnapshot
import java.util.UUID

/**
 * 現在のゲーム状態。
 *
 * @param creatorPlayerId 作成者未設定なら null。
 * @param areaThreshold 作成時に確定する面積閾値（m²）。
 * @param currentArea 進行中の暫定面積と頂点列。進行中以外、または採用位置が3人分未満の場合はnull。
 */
data class GetGameStateResult(
    val gameId: UUID,
    val status: GameStatus,
    val playerCount: Int,
    val creatorPlayerId: UUID?,
    val players: List<PlayerSnapshot>,
    val areaThreshold: Double,
    val currentArea: CurrentArea? = null,
    val result: GameResult? = null,
)
