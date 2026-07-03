package com.github.gaomond.topo.domain.model

import java.util.UUID

/**
 * ゲーム参加の結果（不変値）。サーバー発番した参加者の playerId を保持する。
 *
 * 待機画面 URL（`/game/<gameId>?p=<playerId>`）の組み立てはクライアント責務のため、
 * ここでは playerId のみを返す。
 *
 * @param playerId 参加した player の ID
 */
data class JoinGameResult(
    val playerId: UUID,
)
