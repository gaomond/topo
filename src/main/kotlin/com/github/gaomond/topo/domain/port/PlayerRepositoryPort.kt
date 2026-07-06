package com.github.gaomond.topo.domain.port

import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.model.PlayerReading
import java.time.Instant
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
     * 指定ゲームの参加者一覧を [PlayerReading]（生読み取り・facts）にして返す（US-05: 状態取得 / US-08: live 射影）。
     *
     * live 位置（live_lat / live_lng / live_at）が揃っていれば `live` に載せ、欠けていれば null にする。
     * presence（online）は now を要する policy のため含めない（UseCase が計算する）。
     * joined_at 昇順で返す（参加順表示）。JPA エンティティは露出させない。
     */
    fun findByGameId(gameId: UUID): List<PlayerReading>

    /**
     * ライブ位置（live_lat / live_lng / live_at）を更新する（US-07: 高頻度・副作用なし）。
     *
     * `id = playerId AND game_id = gameId` の一致つき更新で、所属不一致・不在を DB レベルで弾く。
     * 更新できたか（更新行数 > 0）を返す。false は 404（不在 / 非所属）の判定材料（理由は出し分けない）。
     *
     * @param gameId     所属ゲーム ID（一致条件に含める）
     * @param playerId   更新対象の player ID
     * @param coordinate 検証済みライブ座標（Domain 値）
     * @param at         live_at に記録する時刻（UseCase で確定して渡す。presence の last-seen 素材）
     * @return 該当 player が存在し更新できたら true
     */
    fun updateLiveLocation(
        gameId: UUID,
        playerId: UUID,
        coordinate: Coordinate,
        at: Instant,
    ): Boolean
}
