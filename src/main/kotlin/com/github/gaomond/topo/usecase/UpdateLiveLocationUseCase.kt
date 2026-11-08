package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.exception.GameValidationException
import com.github.gaomond.topo.domain.exception.PlayerNotFoundException
import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * ゲーム状態に制限を設けず、ライブ位置・記録時刻だけを更新する。
 * 確定座標やゲームの進行状態は変更しない。同じ座標の再送でも記録時刻は更新する。
 */
@Service
class UpdateLiveLocationUseCase(
    private val playerRepository: PlayerRepositoryPort,
) {
    /**
     * ライブ位置を更新する。
     *
     * @throws GameValidationException 座標が範囲外・NaN・無限。
     * @throws PlayerNotFoundException 対象プレイヤーが不在、または指定ゲームに所属していない。
     */
    @Transactional
    fun update(input: UpdateLiveLocationInput) {
        val gameId = input.gameId
        val playerId = input.playerId
        val lat = input.lat
        val lng = input.lng
        // 座標生成時の違反だけを入力不正へ変換し、他の IllegalArgumentException と区別する。
        val coordinate =
            try {
                Coordinate(lat = lat, lng = lng)
            } catch (e: IllegalArgumentException) {
                throw GameValidationException(
                    GameValidationException.Reason.INVALID_COORDINATE,
                    e.message ?: "座標が不正です",
                )
            }

        // 同じ座標でも在室判定の最終確認時刻を更新する。
        val updated = playerRepository.updateLiveLocation(gameId, playerId, coordinate, Instant.now())
        if (!updated) {
            throw PlayerNotFoundException(gameId, playerId)
        }
    }
}
