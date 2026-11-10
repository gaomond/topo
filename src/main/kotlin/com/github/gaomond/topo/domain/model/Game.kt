package com.github.gaomond.topo.domain.model

import com.github.gaomond.topo.domain.exception.ConfirmationAreaRejectedException
import com.github.gaomond.topo.domain.exception.GameConfirmationNotAllowedException
import com.github.gaomond.topo.domain.exception.GameJoinNotAllowedException
import com.github.gaomond.topo.domain.exception.GameStartNotAllowedException
import com.github.gaomond.topo.domain.exception.GameValidationException
import com.github.gaomond.topo.domain.exception.NotGameCreatorException
import java.time.Instant
import java.util.UUID

enum class GameStatus {
    WAITING,
    ACTIVE,
    COMPLETED,
}

class Game private constructor(
    val gameId: UUID,
    val status: GameStatus,
    val playerCount: Int,
    val creatorPlayerId: UUID?,
    val objectType: ObjectType,
    val areaThreshold: Double,
    val result: GameResult? = null,
) {
    // 待機中でなければ人数取得を行わず拒否する。
    fun requireCanJoin(getJoinedPlayerCount: () -> Int) {
        if (status != GameStatus.WAITING) {
            throw GameJoinNotAllowedException(gameId, GameJoinNotAllowedException.Reason.NOT_WAITING)
        }
        if (getJoinedPlayerCount() >= playerCount) {
            throw GameJoinNotAllowedException(gameId, GameJoinNotAllowedException.Reason.CAPACITY_REACHED)
        }
    }

    // 非作成者へ開始可否を漏らさないよう、状態・人数より先に作成者を判定する。
    // 人数は権限と状態で拒否されなかった場合にだけ参照する。
    fun start(
        playerId: UUID,
        getJoinedPlayerCount: () -> Int,
    ): Game {
        if (creatorPlayerId != playerId) {
            throw NotGameCreatorException(gameId)
        }
        if (status != GameStatus.WAITING) {
            throw GameStartNotAllowedException(gameId, GameStartNotAllowedException.Reason.NOT_WAITING)
        }
        if (getJoinedPlayerCount() != playerCount) {
            throw GameStartNotAllowedException(gameId, GameStartNotAllowedException.Reason.CAPACITY_NOT_REACHED)
        }
        return Game(gameId, GameStatus.ACTIVE, playerCount, creatorPlayerId, objectType, areaThreshold)
    }

    fun requireCanConfirm() {
        if (status != GameStatus.ACTIVE) throw GameConfirmationNotAllowedException(gameId)
    }

    fun validateArea(area: ConfirmationArea): ValidatedConfirmationArea {
        requireCanConfirm()
        check(area.confirmedPlayerCount <= playerCount)
        if (area.areaSqm >
            areaThreshold
        ) {
            throw ConfirmationAreaRejectedException(ConfirmationAreaRejectedException.Reason.AREA_EXCEEDS_THRESHOLD)
        }
        val isFinal = area.confirmedPlayerCount == playerCount
        if (isFinal &&
            area.areaSqm < MIN_AREA_SQM
        ) {
            throw ConfirmationAreaRejectedException(ConfirmationAreaRejectedException.Reason.AREA_BELOW_MIN)
        }
        return ValidatedConfirmationArea(gameId, area, isFinal)
    }

    fun complete(
        validatedArea: ValidatedConfirmationArea,
        objects: List<GameObjectSnapshot>,
    ): Game {
        requireCanConfirm()
        check(validatedArea.gameId == gameId && validatedArea.isFinal)
        check(objects.all { it.objectType == objectType }) { "獲得オブジェクトの種別が一致しません" }
        val area = validatedArea.area
        return Game(
            gameId,
            GameStatus.COMPLETED,
            playerCount,
            creatorPlayerId,
            objectType,
            areaThreshold,
            GameResult(area.areaSqm, true, area.hull, objects),
        )
    }

    companion object {
        private const val MIN_AREA_SQM = 1.0

        private const val MIN_PLAYER_COUNT = 3

        fun create(
            gameId: UUID,
            creatorPlayerId: UUID,
            playerCount: Int,
            objectType: ObjectType,
            areaPreset: AreaPreset,
        ): Game {
            if (playerCount < MIN_PLAYER_COUNT) {
                throw GameValidationException(
                    GameValidationException.Reason.INVALID_PLAYER_COUNT,
                    "playerCount は $MIN_PLAYER_COUNT 以上である必要があります: $playerCount",
                )
            }
            return Game(gameId, GameStatus.WAITING, playerCount, creatorPlayerId, objectType, areaPreset.sqm.toDouble())
        }

        /** 保存済みの状態を復元する入口。新規ゲームには [create] を使う。 */
        fun restore(
            gameId: UUID,
            status: GameStatus,
            playerCount: Int,
            creatorPlayerId: UUID?,
            objectType: ObjectType,
            areaThreshold: Double,
        ): Game = Game(gameId, status, playerCount, creatorPlayerId, objectType, areaThreshold)
    }
}

/**
 * ゲーム状態取得用の読み取り射影。
 *
 * @param creatorPlayerId 作成者の ID。作成者設定前は null。
 * @param areaThreshold 作成時に確定する面積閾値（m²）。
 */
data class GameSummary(
    val status: GameStatus,
    val playerCount: Int,
    val creatorPlayerId: UUID?,
    val areaThreshold: Double,
    val result: GameResult? = null,
)

/**
 * 在室判定を含む参加者のスナップショット。
 *
 * 在室判定は基準時刻を必要とするため、取得した [PlayerReading] から UseCase で計算する。
 *
 * @param live 最新ライブ位置。未送信または座標・記録時刻が欠けている場合は null。
 */
data class PlayerSnapshot(
    val playerId: UUID,
    val displayName: String,
    val confirmedLocation: ConfirmedLocation?,
    val live: LiveLocation? = null,
    val online: Boolean = false,
) {
    val confirmed: Boolean get() = confirmedLocation != null
}

/**
 * 在室判定前の参加者の読み取り射影。
 *
 * 時刻に依存する [Presence] の判定を未計算のまま online=false と表さないよう、
 * [PlayerSnapshot] と型を分ける。
 *
 * @param live 最新ライブ位置。未送信または座標・記録時刻が欠けている場合は null。
 */
data class PlayerReading(
    val playerId: UUID,
    val displayName: String,
    val confirmedLocation: ConfirmedLocation?,
    val live: LiveLocation?,
) {
    val confirmed: Boolean get() = confirmedLocation != null
}

/** 最新ライブ位置。記録時刻 [at] は在室判定の最終確認時刻にも使う。 */
data class LiveLocation(
    val coordinate: Coordinate,
    val at: Instant,
)

/**
 * 確定位置を優先した現在の凸包の面積と頂点列。
 *
 * 採用位置が3人分未満の未計測はnull で表し、一直線・重複による退化面積 0 と区別する。
 *
 * @param sqm 凸包の測地面積（m²）。退化時は 0。
 * @param hull 順序付きの凸包頂点列。多角形では閉環し、退化時は線分または点を持つ。
 */
data class CurrentArea(
    val sqm: Double,
    val hull: List<Coordinate>,
)
