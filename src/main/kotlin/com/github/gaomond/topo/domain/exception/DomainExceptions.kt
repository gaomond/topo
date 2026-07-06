package com.github.gaomond.topo.domain.exception

import java.util.UUID

/**
 * 指定した gameId のゲームが存在しないことを表すドメイン例外。
 *
 * CLAUDE.md「Domain でドメイン例外を定義し、inbound で HTTP ステータスにマッピング
 * （対象不在 = 404）」に従う。inbound（コントローラ）で HTTP 404 にマッピングする。
 *
 * バリデーション失敗（400）を表す [GameValidationException] とは意味が異なるため別型にする。
 */
class GameNotFoundException(
    val gameId: UUID,
) : RuntimeException("ゲームが存在しません: $gameId")

/**
 * ゲーム作成入力のバリデーション失敗を表すドメイン例外。
 *
 * CLAUDE.md「Domain でドメイン例外を定義し、inbound アダプタで HTTP ステータスにマッピング」に従う。
 * 本例外は inbound（コントローラ）で HTTP 400（Bad Request）にマッピングする。
 *
 * 失敗理由を [reason] で識別できるようにする（objectType 不正 / areaPreset 不正 / playerCount 不足 /
 * 座標が意味論的に不正）。
 */
class GameValidationException(
    val reason: Reason,
    message: String,
) : RuntimeException(message) {
    enum class Reason {
        INVALID_OBJECT_TYPE,
        INVALID_AREA_PRESET,
        INVALID_PLAYER_COUNT,

        // ライブ位置の座標が範囲外 / NaN 等で意味論的に不正（US-07 / D4）。
        INVALID_COORDINATE,
    }
}

/**
 * ゲームへの参加が拒否されることを表すドメイン例外。
 *
 * 参加不可の条件（01-spec 1.1 / 1.6）:
 * - status が WAITING 以外（ACTIVE / COMPLETED）: ゲーム開始後は締め切り
 * - 定員（player_count）到達済み
 *
 * inbound（コントローラ）で HTTP 409（Conflict）にマッピングする。
 * 仕様上「理由の出し分けは不要」なので API では 409 の 1 種類に集約する。
 * 内部の [reason] はログ・デバッグ用途にとどめ、レスポンスでは区別しない。
 */
class GameJoinNotAllowedException(
    val gameId: UUID,
    val reason: Reason,
) : RuntimeException("ゲームに参加できません: $gameId ($reason)") {
    enum class Reason {
        NOT_WAITING,
        CAPACITY_REACHED,
    }
}

/**
 * ゲームを開始できない状態であることを表すドメイン例外。
 *
 * 開始不可の条件（01-spec 1.1）:
 * - status が WAITING 以外（ACTIVE / COMPLETED）: 既に開始済み・終了済み（冪等性: 2 回目の開始）
 * - 参加者数 ≠ player_count（定員未達）: N 人揃っていない
 *
 * inbound（コントローラ）で HTTP 409（Conflict）にマッピングする。
 * 仕様上「理由の出し分けは不要」なので API では 409 の 1 種類に集約する。
 * 内部の [reason] はログ・デバッグ用途にとどめ、レスポンスでは区別しない
 * （[GameJoinNotAllowedException] と同方針）。参加とは意味が別のため相乗りしない。
 */
class GameStartNotAllowedException(
    val gameId: UUID,
    val reason: Reason,
) : RuntimeException("ゲームを開始できません: $gameId ($reason)") {
    enum class Reason {
        NOT_WAITING,
        CAPACITY_NOT_REACHED,
    }
}

/**
 * ゲーム開始をリクエストした playerId が作成者（creator）でないことを表すドメイン例外。
 *
 * ゲーム開始（01-spec 1.1）は作成者のみ許可される。リクエスト元 playerId が
 * `game.creator_player_id` と一致しない場合（NULL 含む）に送出する。
 *
 * inbound（コントローラ）で HTTP 403（Forbidden）にマッピングする。
 * 権限（403）は状態・定員（409）より先に評価し、非作成者に内部状態を漏らさない。
 */
class NotGameCreatorException(
    val gameId: UUID,
) : RuntimeException("ゲームの作成者ではありません: $gameId")

/**
 * gameId と playerId の組が解決できない（player 不在 / 当該 game への非所属）ことを表すドメイン例外（US-07）。
 *
 * CLAUDE.md「Domain でドメイン例外を定義し、inbound で HTTP ステータスにマッピング（対象不在 = 404）」に従い、
 * inbound（コントローラ）で HTTP 404 にマッピングする。
 *
 * gameId 単体の不在を表す [GameNotFoundException] とは対象が異なる（player を対象とする）ため別型にする。
 * 不在 / 非所属の理由は出し分けない（spec 1.1 エラーレスポンス）。
 */
class PlayerNotFoundException(
    val gameId: UUID,
    val playerId: UUID,
) : RuntimeException("プレイヤーが存在しません: gameId=$gameId, playerId=$playerId")
