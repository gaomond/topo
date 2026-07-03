package com.github.gaomond.topo.domain

import java.util.UUID

/**
 * ゲームへの参加が拒否されることを表すドメイン例外。
 *
 * 参加不可の条件（01-spec 1.1 / 1.6）:
 * - status が WAITING 以外（ACTIVE / COMPLETED）: ゲーム開始後は締め切り
 * - 定員（player_count）到達済み
 *
 * inbound（コントローラ）で HTTP 409（Conflict）にマッピングする。
 * 仕様上「理由の出し分けは不要」なので API では 409 の 1 種類に集約する。
 * 内部の [reason] はログ・デバッグ用途にとどめ、レスポンスでは区別しない。
 */
class GameJoinNotAllowedException(
    val gameId: UUID,
    val reason: Reason,
) : RuntimeException("ゲームに参加できません: $gameId ($reason)") {
    enum class Reason {
        NOT_WAITING,
        CAPACITY_REACHED,
    }
}
