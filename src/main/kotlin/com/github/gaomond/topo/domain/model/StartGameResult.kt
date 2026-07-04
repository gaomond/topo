package com.github.gaomond.topo.domain.model

import java.util.UUID

/**
 * ゲーム開始の結果（不変値・US-06）。開始後のゲーム ID と状態を保持する。
 *
 * 開始成功時は [status] が [GameStatus.ACTIVE] になる。Web 表現（StartGameResponse）へ
 * 変換して返す。[JoinGameResult] と対称な最小結果型。
 *
 * @param gameId 開始したゲーム ID
 * @param status 開始後の状態（[GameStatus.ACTIVE]）
 */
data class StartGameResult(
    val gameId: UUID,
    val status: GameStatus,
)
