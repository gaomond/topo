package com.github.gaomond.topo.domain

import java.util.UUID

/**
 * ゲームを開始できない状態であることを表すドメイン例外。
 *
 * 開始不可の条件（01-spec 1.1）:
 * - status が WAITING 以外（ACTIVE / COMPLETED）: 既に開始済み・終了済み（冪等性: 2 回目の開始）
 * - 参加者数 ≠ player_count（定員未達）: N 人揃っていない
 *
 * inbound（コントローラ）で HTTP 409（Conflict）にマッピングする。
 * 仕様上「理由の出し分けは不要」なので API では 409 の 1 種類に集約する。
 * 内部の [reason] はログ・デバッグ用途にとどめ、レスポンスでは区別しない
 * （[GameJoinNotAllowedException] と同方針）。参加とは意味が別のため相乗りしない。
 */
class GameStartNotAllowedException(
    val gameId: UUID,
    val reason: Reason,
) : RuntimeException("ゲームを開始できません: $gameId ($reason)") {
    enum class Reason {
        NOT_WAITING,
        CAPACITY_NOT_REACHED,
    }
}
