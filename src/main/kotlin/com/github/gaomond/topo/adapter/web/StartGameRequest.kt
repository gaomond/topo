package com.github.gaomond.topo.adapter.web

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

/**
 * 対象ゲーム ID はパスに指定する。playerId は作成者判定に使う必須項目。
 * 欠如・不正な UUID はデシリアライズに失敗し 400 となる。
 */
data class StartGameRequest(
    @field:Schema(description = "開始を要求するプレイヤーID。作成者のみ開始できる。", requiredMode = Schema.RequiredMode.REQUIRED)
    val playerId: UUID,
)
