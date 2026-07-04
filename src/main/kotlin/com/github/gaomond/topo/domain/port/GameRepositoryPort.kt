package com.github.gaomond.topo.domain.port

import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.GameSummary
import java.util.UUID

/**
 * `game` の永続化ポート（Domain 抽象）。
 *
 * UseCase はこのポートに依存し、JPA 具象（`GameJpaRepository` / `GameJpaEntity`）を import しない（DIP）。
 * 実装は adapter(outbound) 側で JPA リポジトリに適合させる。
 */
interface GameRepositoryPort {
    /**
     * ゲームを新規作成する。
     *
     * 創成順序の 1 段目。この時点では循環回避のため creatorPlayerId は設定しない
     * （後段 [updateCreatorPlayerId] で埋める）。status は [GameStatus.WAITING]、
     * 結果カラム（areaSqm / areaValid / objectCount / polygon）は NULL のままにする。
     *
     * @param gameId        サーバー発番した game ID
     * @param status        初期状態（通常 [GameStatus.WAITING]）
     * @param playerCount   固定参加人数
     * @param objectType    解決済み種別の jsonValue（例: "shrine"）
     * @param areaThreshold 解決済み面積閾値（m²）
     */
    fun createGame(
        gameId: UUID,
        status: GameStatus,
        playerCount: Int,
        objectType: String,
        areaThreshold: Double,
    )

    /**
     * 既存ゲームの creatorPlayerId を設定する（創成順序の 3 段目）。
     */
    fun updateCreatorPlayerId(
        gameId: UUID,
        creatorPlayerId: UUID,
    )

    /**
     * ゲームの status を更新する（US-06: 開始）。status カラムのみ更新し、他カラムは変更しない。
     *
     * @param gameId 対象ゲーム ID
     * @param status 更新後の状態（開始時は [GameStatus.ACTIVE]）
     */
    fun updateStatus(
        gameId: UUID,
        status: GameStatus,
    )

    /**
     * 参加・開始判定に必要なゲームサマリ（status / playerCount / creatorPlayerId）を取得する。
     * 存在しなければ null。
     *
     * JPA エンティティは露出させず、Domain 値 [GameSummary] に射影して返す（US-05: 参加・状態取得 / US-06: 開始）。
     */
    fun findSummary(gameId: UUID): GameSummary?
}
