package com.github.gaomond.topo.domain.port

import com.github.gaomond.topo.domain.model.Game
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.GameSummary
import java.util.UUID

interface GameRepositoryPort {
    /**
     * ゲームを新規作成する。作成者との外部キー循環を避けるため creatorPlayerId は設定せず、
     * 作成者の保存後に [updateCreatorPlayerId] で設定する。結果は未確定（null）とする。
     *
     * @param areaThreshold 解決済みの面積閾値（m²）。
     */
    fun createGame(
        gameId: UUID,
        status: GameStatus,
        playerCount: Int,
        objectType: String,
        areaThreshold: Double,
    )

    /** 保存済みの作成者をゲームに設定する。対象ゲームは存在すること。 */
    fun updateCreatorPlayerId(
        gameId: UUID,
        creatorPlayerId: UUID,
    )

    /** status のみを更新し、結果・空間カラムを含む他の値は維持する。対象ゲームは存在すること。 */
    fun updateStatus(
        gameId: UUID,
        status: GameStatus,
    )

    /**
     * 状態取得用の [GameSummary] を返す。不在なら null。
     * 参加・開始の判定には [findByIdWithLock] を使う。
     */
    fun findSummary(gameId: UUID): GameSummary?

    /**
     * ゲーム行をロックし、操作用の [Game] を復元して返す。不在なら null。
     * 書き込みトランザクションを必須とし、その終了までロックを保持する。
     */
    fun findByIdWithLock(gameId: UUID): Game?
}
