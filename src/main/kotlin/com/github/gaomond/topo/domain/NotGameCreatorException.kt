package com.github.gaomond.topo.domain

import java.util.UUID

/**
 * ゲーム開始をリクエストした playerId が作成者（creator）でないことを表すドメイン例外。
 *
 * ゲーム開始（01-spec 1.1）は作成者のみ許可される。リクエスト元 playerId が
 * `game.creator_player_id` と一致しない場合（NULL 含む）に送出する。
 *
 * inbound（コントローラ）で HTTP 403（Forbidden）にマッピングする。
 * 権限（403）は状態・定員（409）より先に評価し、非作成者に内部状態を漏らさない。
 */
class NotGameCreatorException(
    val gameId: UUID,
) : RuntimeException("ゲームの作成者ではありません: $gameId")
