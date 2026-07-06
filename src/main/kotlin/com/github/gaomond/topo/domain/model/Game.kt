package com.github.gaomond.topo.domain.model

import java.util.UUID

/**
 * ゲームの状態（status）。
 *
 * DESIGN.md / DB enum 型 `game_status`（'WAITING' / 'ACTIVE' / 'COMPLETED'）に対応する。
 * enum 名がそのまま DB enum の値・JSON 値と一致するため、変換用フィールドは持たない。
 *
 * 状態遷移ロジック（WAITING → ACTIVE → COMPLETED の条件判定）は本 enum では持たない。
 * 遷移は US-05 / US-06 / US-13 のスコープであり、本ストーリーは「格納できる値の定義」のみを担う。
 */
enum class GameStatus {
    WAITING,
    ACTIVE,
    COMPLETED,
}

/**
 * 参加・開始判定に必要なゲームの軽量サマリ（Domain 値）。
 *
 * ポート [com.github.gaomond.topo.domain.port.GameRepositoryPort.findSummary] が返す。
 * JPA エンティティを UseCase に露出させないため、判定・露出に必要なカラムのみに射影する。
 *
 * @param status          現在の状態（WAITING 判定用）
 * @param playerCount     固定参加人数（定員判定用）
 * @param creatorPlayerId 作成者の playerId（開始 API の creator 判定=403 / GET 露出用）。
 *                        創成順序上、作成直後の一瞬は NULL のため nullable を維持する。
 */
data class GameSummary(
    val status: GameStatus,
    val playerCount: Int,
    val creatorPlayerId: UUID?,
)

/**
 * ゲームの現在状態（Domain ビュー・最小形）。GET /api/games/{id} が返す土台。
 *
 * US-05 スコープでは待機（WAITING）中心のため、status / playerCount / players のみを持つ。
 * live 座標・currentArea・result は US-07〜10 で拡張する領域のため本型には含めない
 * （将来のキー追加で破壊的変更なく拡張できる形）。Spring / JPA 非依存。
 *
 * @param gameId          ゲーム ID
 * @param status          現在の状態
 * @param playerCount     固定参加人数（定員）
 * @param creatorPlayerId 作成者の playerId（フロントの開始ボタン creator 判定用・US-06）。
 *                        作成直後の一瞬を除き設定済みだが型は nullable を維持する。
 * @param players         参加者一覧
 */
data class GameState(
    val gameId: UUID,
    val status: GameStatus,
    val playerCount: Int,
    val creatorPlayerId: UUID?,
    val players: List<PlayerSnapshot>,
)

/**
 * 参加者のスナップショット（ある時点の読み取り射影・最小形）。Spring / JPA 非依存。
 *
 * @param playerId    プレイヤー ID
 * @param displayName 確定済み表示名（常に非 null）
 * @param confirmed   現在地を確定済みか（confirmedAt != null）
 */
data class PlayerSnapshot(
    val playerId: UUID,
    val displayName: String,
    val confirmed: Boolean,
)
