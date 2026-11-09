package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.exception.GameNotFoundException
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.PlayerSnapshot
import com.github.gaomond.topo.domain.model.Presence
import com.github.gaomond.topo.domain.port.GameRepositoryPort
import com.github.gaomond.topo.domain.port.LiveAreaQueryPort
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * 全参加者に共通の基準時刻で在室判定を行う。
 * 暫定面積の計算は進行中に限り、待機中・完了後には行わない。
 */
@Service
class GetGameStateUseCase(
    private val gameRepository: GameRepositoryPort,
    private val playerRepository: PlayerRepositoryPort,
    private val liveAreaQuery: LiveAreaQueryPort,
    private val clock: Clock,
) {
    /**
     * ゲームの現在状態を返す。
     *
     * @throws GameNotFoundException 対象ゲームが存在しない。
     */
    @Transactional(readOnly = true)
    fun getState(input: GetGameStateInput): GetGameStateResult {
        val gameId = input.gameId
        val summary =
            gameRepository.findSummary(gameId)
                ?: throw GameNotFoundException(gameId)

        val now = clock.instant()
        val players =
            playerRepository.findByGameId(gameId).map { reading ->
                PlayerSnapshot(
                    playerId = reading.playerId,
                    displayName = reading.displayName,
                    confirmed = reading.confirmed,
                    live = reading.live,
                    online = Presence.isOnline(reading.live?.at, now),
                )
            }

        val currentArea =
            if (summary.status == GameStatus.ACTIVE) liveAreaQuery.currentLiveArea(gameId) else null

        return GetGameStateResult(
            gameId = gameId,
            status = summary.status,
            playerCount = summary.playerCount,
            creatorPlayerId = summary.creatorPlayerId,
            players = players,
            areaThreshold = summary.areaThreshold,
            currentArea = currentArea,
            result = summary.result,
        )
    }
}
