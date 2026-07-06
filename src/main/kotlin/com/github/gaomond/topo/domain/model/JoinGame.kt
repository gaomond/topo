package com.github.gaomond.topo.domain.model

import java.util.UUID

/**
 * ゲーム参加の入力（不変値）。UseCase が受け取るドメイン入力。
 *
 * Web 表現（`JoinGameRequest`）とは別型にし、境界（inbound）で変換する
 * （US-04 の [GameCreationCommand] に対称）。Spring / JPA 非依存。
 *
 * @param gameId      参加対象ゲームの ID（共有キー。パス変数由来）
 * @param displayName 参加者の表示名。任意（null / 空 / 空白のみはサーバーがフォールバック）
 */
data class JoinGameCommand(
    val gameId: UUID,
    val displayName: String? = null,
)

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
