package com.github.gaomond.topo.adapter.web

import java.util.UUID

/**
 * POST /api/games/{id}/start のリクエストボディ（Web 表現・US-06）。
 *
 * gameId はパス変数から渡す（ボディには含めない）。playerId は creator 判定に使う必須項目。
 * 必須のため nullable にせず、欠如時は Jackson のデシリアライズが失敗し 400 になる。
 *
 * @param playerId リクエスト元 playerId（creator 判定用）
 */
data class StartGameRequest(
    val playerId: UUID,
)
