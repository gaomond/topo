package com.github.gaomond.topo.domain

/**
 * ゲーム作成入力のバリデーション失敗を表すドメイン例外。
 *
 * CLAUDE.md「Domain でドメイン例外を定義し、inbound アダプタで HTTP ステータスにマッピング」に従う。
 * 本例外は inbound（コントローラ）で HTTP 400（Bad Request）にマッピングする。
 *
 * 失敗理由を [reason] で識別できるようにする（objectType 不正 / areaPreset 不正 / playerCount 不足）。
 */
class GameValidationException(
    val reason: Reason,
    message: String,
) : RuntimeException(message) {
    enum class Reason {
        INVALID_OBJECT_TYPE,
        INVALID_AREA_PRESET,
        INVALID_PLAYER_COUNT,
    }
}
