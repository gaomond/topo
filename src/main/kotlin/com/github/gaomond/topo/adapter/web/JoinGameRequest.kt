package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.JoinGameInput
import java.util.UUID

/**
 * 参加のリクエストボディ。対象ゲーム ID はパスに指定する。
 *
 * @param displayName 任意。未送信・null・空・空白のみは表示名を補完する。
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
