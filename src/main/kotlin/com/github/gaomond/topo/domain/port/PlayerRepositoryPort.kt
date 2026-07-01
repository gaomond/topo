package com.github.gaomond.topo.domain.port

import java.util.UUID

/**
 * `player` の永続化ポート（Domain 抽象）。
 *
 * UseCase はこのポートに依存し、JPA 具象（`PlayerRepository` / `PlayerJpaEntity`）を import しない（DIP）。
 */
interface PlayerRepositoryPort {
    /**
     * プレイヤーを新規作成する（創成順序の 2 段目）。
     *
     * displayName は UseCase 側でフォールバック解決済み（常に非 null）で渡す。
     *
     * @param playerId    サーバー発番した player ID
     * @param gameId      所属ゲーム ID
     * @param displayName 確定済み表示名（NULL にしない。D6 案A）
     */
    fun createPlayer(
        playerId: UUID,
        gameId: UUID,
        displayName: String,
    )
}
