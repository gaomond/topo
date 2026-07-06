package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.exception.GameValidationException
import com.github.gaomond.topo.domain.exception.PlayerNotFoundException
import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * ライブ位置更新ユースケース（01-spec 1.1 のサーバー処理・US-07）。
 *
 * 座標を [Coordinate] に構築（不正値→400）→ live_at 時刻を確定 → ポートで一致つき更新 →
 * 更新 0 件なら [PlayerNotFoundException]（404）、を 1 トランザクションで実行する。
 *
 * 状態ガードは設けない（D1 案B）。`game.status` を参照せず、WAITING / ACTIVE / COMPLETED いずれも受理する
 * （副作用なし・冪等。クライアントは ACTIVE の地図画面でのみ送信する）。
 *
 * 依存は Domain ポートのみで JPA 具象・PostGIS に依存しない（DIP / Clean Architecture）。
 */
@Service
class UpdateLiveLocationUseCase(
    private val playerRepository: PlayerRepositoryPort,
) {
    /**
     * ライブ位置を更新する（副作用なし・返り値なし＝204 相当）。
     *
     * @param gameId   所属ゲーム ID
     * @param playerId 更新対象 player ID
     * @param lat      緯度（範囲外 / NaN は 400）
     * @param lng      経度（範囲外 / NaN は 400）
     * @throws GameValidationException 座標が意味論的に不正（→400）
     * @throws PlayerNotFoundException gameId / playerId の組が解決できない（不在 / 非所属。→404）
     */
    @Transactional
    fun update(
        gameId: UUID,
        playerId: UUID,
        lat: Double,
        lng: Double,
    ) {
        // Coordinate の不変条件違反（IllegalArgumentException）は 400 経路の GameValidationException に包み直す
        // （既存 400 経路への一貫化。ハンドラで IllegalArgumentException を広く 400 化しない）。
        val coordinate =
            try {
                Coordinate(lat = lat, lng = lng)
            } catch (e: IllegalArgumentException) {
                throw GameValidationException(
                    GameValidationException.Reason.INVALID_COORDINATE,
                    e.message ?: "座標が不正です",
                )
            }

        // live_at は「必ず現在時刻へ更新」する（presence の last-seen 素材。TTL 判定は US-08）。
        val updated = playerRepository.updateLiveLocation(gameId, playerId, coordinate, Instant.now())
        if (!updated) {
            throw PlayerNotFoundException(gameId, playerId)
        }
    }
}
