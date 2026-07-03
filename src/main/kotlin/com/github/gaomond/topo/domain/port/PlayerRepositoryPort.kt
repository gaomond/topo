package com.github.gaomond.topo.domain.port

import com.github.gaomond.topo.domain.model.PlayerSnapshot
import java.util.UUID

/**
 * `player` の永続化ポート（Domain 抽象）。
 *
 * UseCase はこのポートに依存し、JPA 具象（`PlayerJpaRepository` / `PlayerJpaEntity`）を import しない（DIP）。
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

    /**
     * 指定ゲームの現在の参加者数を返す（定員チェック用。US-05: 参加）。
     */
    fun countByGameId(gameId: UUID): Int

    /**
     * 指定ゲームの参加者一覧を [PlayerSnapshot]（読み取り射影）にして返す（US-05: 状態取得）。
     *
     * joined_at 昇順で返す（参加順表示）。JPA エンティティは露出させない。
     */
    fun findByGameId(gameId: UUID): List<PlayerSnapshot>
}
