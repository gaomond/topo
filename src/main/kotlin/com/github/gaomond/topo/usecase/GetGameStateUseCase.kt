package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.GameNotFoundException
import com.github.gaomond.topo.domain.model.GameState
import com.github.gaomond.topo.domain.port.GameRepositoryPort
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * ゲーム状態取得ユースケース（01-spec 1.3 / 1.4 / 1.5 が依存する GET）。
 *
 * gameId から status / playerCount / players（playerId・displayName・confirmed）の最小状態を返す。
 * live 座標・currentArea・result は US-07〜10 のスコープのため本ストーリーでは含めない。
 *
 * 依存は Domain ポートのみ（DIP）。
 */
@Service
class GetGameStateUseCase(
    private val gameRepository: GameRepositoryPort,
    private val playerRepository: PlayerRepositoryPort,
) {
    /**
     * gameId の現在状態を返す。
     *
     * @throws GameNotFoundException gameId が存在しない（→404。フロント 1.4 の 404 画面）
     */
    @Transactional(readOnly = true)
    fun getState(gameId: UUID): GameState {
        val summary =
            gameRepository.findSummary(gameId)
                ?: throw GameNotFoundException(gameId)
        val players = playerRepository.findByGameId(gameId)
        return GameState(
            gameId = gameId,
            status = summary.status,
            playerCount = summary.playerCount,
            creatorPlayerId = summary.creatorPlayerId,
            players = players,
        )
    }
}
