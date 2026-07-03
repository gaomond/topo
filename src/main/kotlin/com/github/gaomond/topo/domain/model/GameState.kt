package com.github.gaomond.topo.domain.model

import java.util.UUID

/**
 * ゲームの現在状態（Domain ビュー・最小形）。GET /api/games/{id} が返す土台。
 *
 * US-05 スコープでは待機（WAITING）中心のため、status / playerCount / players のみを持つ。
 * live 座標・currentArea・result は US-07〜10 で拡張する領域のため本型には含めない
 * （将来のキー追加で破壊的変更なく拡張できる形）。Spring / JPA 非依存。
 *
 * @param gameId      ゲーム ID
 * @param status      現在の状態
 * @param playerCount 固定参加人数（定員）
 * @param players     参加者一覧
 */
data class GameState(
    val gameId: UUID,
    val status: GameStatus,
    val playerCount: Int,
    val players: List<PlayerView>,
)

/**
 * 参加者の Domain ビュー（最小形）。
 *
 * @param playerId    プレイヤー ID
 * @param displayName 確定済み表示名（常に非 null）
 * @param confirmed   現在地を確定済みか（confirmedAt != null）
 */
data class PlayerView(
    val playerId: UUID,
    val displayName: String,
    val confirmed: Boolean,
)
