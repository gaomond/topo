package com.github.gaomond.topo.domain.model

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
