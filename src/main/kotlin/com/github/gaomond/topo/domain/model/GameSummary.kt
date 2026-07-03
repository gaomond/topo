package com.github.gaomond.topo.domain.model

/**
 * 参加判定に必要なゲームの軽量サマリ（Domain 値）。
 *
 * ポート [com.github.gaomond.topo.domain.port.GameRepositoryPort.findSummary] が返す。
 * JPA エンティティを UseCase に露出させないため、status / playerCount のみに射影する。
 *
 * @param status      現在の状態（WAITING 判定用）
 * @param playerCount 固定参加人数（定員判定用）
 */
data class GameSummary(
    val status: GameStatus,
    val playerCount: Int,
)
