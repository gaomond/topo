package com.github.gaomond.topo.domain.model

import com.github.gaomond.topo.domain.exception.GameJoinNotAllowedException
import com.github.gaomond.topo.domain.exception.GameStartNotAllowedException
import com.github.gaomond.topo.domain.exception.NotGameCreatorException
import java.time.Instant
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

/** 参加ルールと開始時の状態遷移を担うゲーム。 */
data class Game(
    val gameId: UUID,
    val status: GameStatus,
    val playerCount: Int,
    val creatorPlayerId: UUID?,
) {
    // 人数は状態や権限で拒否できる場合には取得しない。
    fun requireCanJoin(joinedCount: () -> Int) {
        if (status != GameStatus.WAITING) {
            throw GameJoinNotAllowedException(gameId, GameJoinNotAllowedException.Reason.NOT_WAITING)
        }
        if (joinedCount() >= playerCount) {
            throw GameJoinNotAllowedException(gameId, GameJoinNotAllowedException.Reason.CAPACITY_REACHED)
        }
    }

    fun start(
        playerId: UUID,
        joinedCount: () -> Int,
    ): Game {
        if (creatorPlayerId != playerId) {
            throw NotGameCreatorException(gameId)
        }
        if (status != GameStatus.WAITING) {
            throw GameStartNotAllowedException(gameId, GameStartNotAllowedException.Reason.NOT_WAITING)
        }
        if (joinedCount() != playerCount) {
            throw GameStartNotAllowedException(gameId, GameStartNotAllowedException.Reason.CAPACITY_NOT_REACHED)
        }
        return copy(status = GameStatus.ACTIVE)
    }
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
 * @param areaThreshold   面積成立判定の上限（m²）。作成時に確定（DB `area_threshold`・NOT NULL）。
 *                        US-10 の面積メーターが「満杯ライン」に使うため GET のリードパスへ射影する。
 */
data class GameSummary(
    val status: GameStatus,
    val playerCount: Int,
    val creatorPlayerId: UUID?,
    val areaThreshold: Double,
) {
    fun toGame(gameId: UUID): Game = Game(gameId, status, playerCount, creatorPlayerId)
}

/**
 * ゲームの現在状態（Domain ビュー）。GET /api/games/{id} が返す土台。
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
data class GameState(
    val gameId: UUID,
    val status: GameStatus,
    val playerCount: Int,
    val creatorPlayerId: UUID?,
    val players: List<PlayerSnapshot>,
    val areaThreshold: Double,
    val currentArea: CurrentArea? = null,
)

/**
 * 参加者のスナップショット（ある時点の読み取り射影）。Spring / JPA 非依存。
 *
 * `online`（presence）は now を要する policy のため repo では算出せず、UseCase が [live] の時刻から計算する
 * （facts=repo の [PlayerReading] / policy=usecase の [PlayerSnapshot] を分離）。
 *
 * @param playerId    プレイヤー ID
 * @param displayName 確定済み表示名（常に非 null）
 * @param confirmed   現在地を確定済みか（confirmedAt != null）
 * @param live        最新ライブ位置（未送信は null・US-08）
 * @param online      live_at の鮮度（TTL 内）による在室判定（サーバー計算・US-08）
 */
data class PlayerSnapshot(
    val playerId: UUID,
    val displayName: String,
    val confirmed: Boolean,
    val live: LiveLocation? = null,
    val online: Boolean = false,
)

/**
 * 参加者の生読み取り（facts）。repo が返す presence 未計算の射影。
 *
 * online（TTL 判定）は now を要する policy のため含めない。UseCase が本値に [Presence] を適用して
 * [PlayerSnapshot] を組み立てる（プレースホルダ online=false の footgun を避けるための型分割）。
 *
 * @param playerId    プレイヤー ID
 * @param displayName 確定済み表示名（常に非 null）
 * @param confirmed   現在地を確定済みか（confirmedAt != null）
 * @param live        最新ライブ位置（未送信は null）
 */
data class PlayerReading(
    val playerId: UUID,
    val displayName: String,
    val confirmed: Boolean,
    val live: LiveLocation?,
)

/**
 * 最新ライブ位置（ユビキタス言語「ライブ位置 = liveLocation」・US-07/08）。
 *
 * プレイヤーの現在地 1 点（[coordinate]）と、それを記録した時刻（[at]）を持つ。
 * [at] は presence（在室判定）の last-seen 素材でもある。純 Kotlin。
 *
 * @param coordinate 緯度経度
 * @param at         記録時刻（live_at）
 */
data class LiveLocation(
    val coordinate: Coordinate,
    val at: Instant,
)

/**
 * live 位置の暫定凸包の面積と頂点列（US-08 進行中メーター）。
 *
 * 3 点未満で凸包が多角形として成立しないケースは「そもそも本型を作らない（null）」で表現し、
 * 退化（一直線・重複で面積ゼロ）の `sqm = 0` とは区別する。凸包・面積はサーバー（PostGIS）計算で、
 * クライアントは [hull] をそのまま描画する（DRY・改ざん防止）。純 Kotlin。
 *
 * @param sqm  凸包の測地面積（m²・`ST_Area(::geography)`）。退化時は 0。
 * @param hull 凸包の頂点列（順序付き閉環）。退化時は成立する頂点列（線分・点）をそのまま持つ。
 */
data class CurrentArea(
    val sqm: Double,
    val hull: List<Coordinate>,
)
