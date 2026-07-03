package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.model.JoinGameCommand
import java.util.UUID

/**
 * POST /api/games/{id}/players のリクエストボディ（Web 表現）。
 *
 * Domain 入力（[JoinGameCommand]）とは別型にし、ここで JSON ↔ ドメイン入力の境界を持つ。
 * gameId はパス変数から渡す（ボディには含めない）。
 *
 * @param displayName 参加者の表示名。任意（未送信/null ならサーバーがフォールバック）
 */
data class JoinGameRequest(
    val displayName: String? = null,
) {
    fun toCommand(gameId: UUID): JoinGameCommand =
        JoinGameCommand(
            gameId = gameId,
            displayName = displayName,
        )
}
