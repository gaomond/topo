package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.exception.PlayerNotFoundException
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
     * @throws PlayerNotFoundException 対象プレイヤーが不在、または指定ゲームに所属していない。
     */
    @Transactional
    fun update(input: UpdateLiveLocationInput) {
        val gameId = input.gameId
        val playerId = input.playerId
        // 同じ座標でも在室判定の最終確認時刻を更新する。
        val updated = playerRepository.updateLiveLocation(gameId, playerId, input.coordinate, Instant.now())
        if (!updated) {
            throw PlayerNotFoundException(gameId, playerId)
        }
    }
}
