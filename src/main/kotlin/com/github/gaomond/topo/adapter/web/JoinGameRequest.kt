package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.JoinGameInput
import java.util.UUID

/**
 * POST /api/games/{id}/players のリクエストボディ（Web 表現）。
 *
 * UseCase 入力（[JoinGameInput]）とは別型にし、ここで JSON ↔ UseCase 入力の境界を持つ。
 * gameId はパス変数から渡す（ボディには含めない）。
 *
 * @param displayName 参加者の表示名。任意（未送信/null ならサーバーがフォールバック）
 */
data class JoinGameRequest(
    val displayName: String? = null,
) {
    fun toInput(gameId: UUID): JoinGameInput =
        JoinGameInput(
            gameId = gameId,
            displayName = displayName,
        )
}
