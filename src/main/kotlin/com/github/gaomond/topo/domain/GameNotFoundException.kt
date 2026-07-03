package com.github.gaomond.topo.domain

import java.util.UUID

/**
 * 指定した gameId のゲームが存在しないことを表すドメイン例外。
 *
 * CLAUDE.md「Domain でドメイン例外を定義し、inbound で HTTP ステータスにマッピング
 * （対象不在 = 404）」に従う。inbound（コントローラ）で HTTP 404 にマッピングする。
 *
 * バリデーション失敗（400）を表す [GameValidationException] とは意味が異なるため別型にする。
 */
class GameNotFoundException(
    val gameId: UUID,
) : RuntimeException("ゲームが存在しません: $gameId")
