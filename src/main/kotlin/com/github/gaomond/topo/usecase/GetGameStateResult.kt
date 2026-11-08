package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.model.CurrentArea
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.PlayerSnapshot
import java.util.UUID

/**
 * ゲーム状態取得の結果。GET /api/games/{id} が返す土台。
 *
 * status / playerCount / players に加え、US-08 で進行中メーター用の [currentArea]（live 位置の
 * 暫定凸包面積・頂点列）を持つ。`result`（確定結果）は confirm/集計（US-11/13）まで null であり、
 * 型自体は将来のキー追加で破壊的変更なく拡張できる形を維持する。Spring / JPA 非依存。
 *
 * @param gameId          ゲーム ID
 * @param status          現在の状態
 * @param playerCount     固定参加人数（定員）
 * @param creatorPlayerId 作成者の playerId（フロントの開始ボタン creator 判定用・US-06）。
 *                        作成直後の一瞬を除き設定済みだが型は nullable を維持する。
 * @param players         参加者一覧
 * @param areaThreshold   面積成立判定の上限（m²）。作成時確定の静的値（playerCount / creatorPlayerId と同列）。
 *                        US-10 の面積メーターが上限ラインに使う。currentArea.sqm はこの値と比較される。
 * @param currentArea     live 位置の暫定凸包面積・頂点列（US-08）。ACTIVE かつ live 点が 3 点以上の
 *                        ときのみ非 null。WAITING / COMPLETED や live 点 < 3 では null。
 */
data class GetGameStateResult(
    val gameId: UUID,
    val status: GameStatus,
    val playerCount: Int,
    val creatorPlayerId: UUID?,
    val players: List<PlayerSnapshot>,
    val areaThreshold: Double,
    val currentArea: CurrentArea? = null,
)
