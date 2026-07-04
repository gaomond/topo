package com.github.gaomond.topo.domain.model

import java.util.UUID

/**
 * 参加・開始判定に必要なゲームの軽量サマリ（Domain 値）。
 *
 * ポート [com.github.gaomond.topo.domain.port.GameRepositoryPort.findSummary] が返す。
 * JPA エンティティを UseCase に露出させないため、判定・露出に必要なカラムのみに射影する。
 *
 * @param status          現在の状態（WAITING 判定用）
 * @param playerCount     固定参加人数（定員判定用）
 * @param creatorPlayerId 作成者の playerId（開始 API の creator 判定=403 / GET 露出用）。
 *                        創成順序上、作成直後の一瞬は NULL のため nullable を維持する。
 */
data class GameSummary(
    val status: GameStatus,
    val playerCount: Int,
    val creatorPlayerId: UUID?,
)
