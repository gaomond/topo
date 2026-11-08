package com.github.gaomond.topo.domain.port

import com.github.gaomond.topo.domain.model.Game
import com.github.gaomond.topo.domain.model.GameSummary
import java.util.UUID

interface GameRepositoryPort {
    /**
     * ゲームを作成者ID未設定で新規保存する。モデル自体の作成者IDは維持する。
     * 作成者との外部キー循環を避けるためDBのcreatorPlayerIdは設定せず、
     * 作成者の保存後に [updateCreatorPlayerId] で設定する。結果は未確定（null）とする。
     */
    fun createWithoutCreator(game: Game)

    /** 作成者IDのみを保存する。対象ゲームは存在し、モデルの作成者IDは非nullであること。 */
    fun updateCreatorPlayerId(game: Game)

    /** status のみを更新し、結果・空間カラムを含む他の値は維持する。対象ゲームは存在すること。 */
    fun updateStatus(game: Game)

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
