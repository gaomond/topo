package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.JoinGameInput
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

/**
 * 参加のリクエストボディ。対象ゲーム ID はパスに指定する。
 *
 * @param displayName 任意。未送信・null・空・空白のみは表示名を補完する。
 */
data class JoinGameRequest(
    @field:Schema(description = "省略・null・空・空白のみの場合はプレイヤーIDの先頭8文字で補完する。")
    val displayName: String? = null,
) {
    fun toInput(gameId: UUID): JoinGameInput =
        JoinGameInput(
            gameId = gameId,
            displayName = displayName,
        )
}
