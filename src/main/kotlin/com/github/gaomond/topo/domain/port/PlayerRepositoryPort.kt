package com.github.gaomond.topo.domain.port

import com.github.gaomond.topo.domain.model.ConfirmedLocation
import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.model.Player
import com.github.gaomond.topo.domain.model.PlayerReading
import java.time.Instant
import java.util.UUID

interface PlayerRepositoryPort {
    /**
     * プレイヤーを新規作成する。所属ゲームは保存済みであること。
     */
    fun createPlayer(player: Player)

    /** 所属を限定して操作用Playerを復元する。不在・非所属はいずれもnull。 */
    fun findByGameIdAndPlayerId(
        gameId: UUID,
        playerId: UUID,
    ): Player?

    /** ゲーム行のロックと確定可否の判定後、同一トランザクション内で固定点・時刻のみを保存する。 */
    fun confirmLocation(
        gameId: UUID,
        playerId: UUID,
        location: ConfirmedLocation,
    )

    fun countByGameId(gameId: UUID): Int

    /**
     * 参加順で一覧を返す。不在のゲームや参加者がいない場合は空リスト。
     *
     * ライブ位置の座標・記録時刻が揃わなければ live は null とする。
     * 在室判定は基準時刻を要するためここでは行わない。
     */
    fun findByGameId(gameId: UUID): List<PlayerReading>

    /**
     * 所属ゲームとプレイヤー ID の両方が一致する場合のみライブ位置・記録時刻を更新する。
     * 確定座標など、他の値は維持する。不在・所属不一致はいずれも false を返す。
     *
     * @param at 在室判定の最終確認時刻として記録する時刻。
     */
    fun updateLiveLocation(
        gameId: UUID,
        playerId: UUID,
        coordinate: Coordinate,
        at: Instant,
    ): Boolean
}
