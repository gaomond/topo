package com.github.gaomond.topo.domain.exception

import java.util.UUID

/** 緯度経度が座標の不変条件を満たさない。 */
class InvalidCoordinateException(
    message: String,
) : RuntimeException(message)

/** 対象ゲームが存在せず、操作の前提が満たされない。 */
class GameNotFoundException(
    val gameId: UUID,
) : RuntimeException("ゲームが存在しません: $gameId")

/** ゲーム設定の値が不正。失敗理由は [reason] で識別する。 */
class GameValidationException(
    val reason: Reason,
    message: String,
) : RuntimeException(message) {
    enum class Reason {
        INVALID_OBJECT_TYPE,
        INVALID_AREA_PRESET,
        INVALID_PLAYER_COUNT,
    }
}

/** 待機中ではない、または定員に達しているため参加できない。 */
class GameJoinNotAllowedException(
    val gameId: UUID,
    val reason: Reason,
) : RuntimeException("ゲームに参加できません: $gameId ($reason)") {
    enum class Reason {
        NOT_WAITING,
        CAPACITY_REACHED,
    }
}

/** 待機中ではない、または参加者数が定員と一致しないため開始できない。 */
class GameStartNotAllowedException(
    val gameId: UUID,
    val reason: Reason,
) : RuntimeException("ゲームを開始できません: $gameId ($reason)") {
    enum class Reason {
        NOT_WAITING,
        CAPACITY_NOT_REACHED,
    }
}

/** 操作したプレイヤーが作成者と一致しない。作成者未設定の場合も含む。 */
class NotGameCreatorException(
    val gameId: UUID,
) : RuntimeException("ゲームの作成者ではありません: $gameId")

/** 対象プレイヤーが存在しない、または指定ゲームに所属していない。 */
class PlayerNotFoundException(
    val gameId: UUID,
    val playerId: UUID,
) : RuntimeException("プレイヤーが存在しません: gameId=$gameId, playerId=$playerId")

/** ゲームが確定を受け付ける状態ではない。 */
class GameConfirmationNotAllowedException(
    val gameId: UUID,
) : RuntimeException("このゲームでは確定できません: $gameId")

/** 保存済みの確定点は変更できない。 */
class PlayerAlreadyConfirmedException(
    val playerId: UUID,
) : RuntimeException("プレイヤーは確定済みです: $playerId")
