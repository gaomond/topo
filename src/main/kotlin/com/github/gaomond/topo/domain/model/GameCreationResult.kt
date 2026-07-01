package com.github.gaomond.topo.domain.model

import java.util.UUID

/**
 * ゲーム作成の結果（不変値）。サーバー発番した gameId / playerId を保持する。
 *
 * 招待 URL / 作成者 URL の組み立てはクライアント責務のため、ここでは ID のみを返す。
 *
 * @param gameId   作成されたゲームの ID（共有キー）
 * @param playerId 作成者（creator）の player ID
 */
data class GameCreationResult(
    val gameId: UUID,
    val playerId: UUID,
)
