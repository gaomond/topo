package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.exception.GameNotFoundException
import com.github.gaomond.topo.domain.model.GameState
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.PlayerSnapshot
import com.github.gaomond.topo.domain.model.Presence
import com.github.gaomond.topo.domain.port.GameRepositoryPort
import com.github.gaomond.topo.domain.port.LiveAreaQueryPort
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * ゲーム状態取得ユースケース（GET /api/games/{id}）。
 *
 * gameId から status / playerCount / players を返す土台に、US-08 で以下を組み立てる:
 * - `players[].online`: [Presence] を live_at と now（[clock]）に適用した在室判定（サーバー計算）。
 * - `currentArea`: **ACTIVE のときのみ** [LiveAreaQueryPort] で live 位置の暫定凸包面積を得る。
 *   WAITING は live 未送信で必然 null、COMPLETED は result 側で currentArea は null。
 *
 * 依存は Domain ポート（と時刻源 [Clock]）のみ（DIP）。PostGIS / JPA 具象を import しない。
 */
@Service
class GetGameStateUseCase(
    private val gameRepository: GameRepositoryPort,
    private val playerRepository: PlayerRepositoryPort,
    private val liveAreaQuery: LiveAreaQueryPort,
    private val clock: Clock,
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

        val now = clock.instant()
        val players =
            playerRepository.findByGameId(gameId).map { reading ->
                PlayerSnapshot(
                    playerId = reading.playerId,
                    displayName = reading.displayName,
                    confirmed = reading.confirmed,
                    live = reading.live,
                    // presence は now を要する policy のためここ（UseCase）で計算する。
                    online = Presence.isOnline(reading.live?.at, now),
                )
            }

        // 進行中メーターの暫定面積は ACTIVE のときだけ計算する（空間クエリの発火も ACTIVE 限定）。
        val currentArea =
            if (summary.status == GameStatus.ACTIVE) liveAreaQuery.currentLiveArea(gameId) else null

        return GameState(
            gameId = gameId,
            status = summary.status,
            playerCount = summary.playerCount,
            creatorPlayerId = summary.creatorPlayerId,
            players = players,
            currentArea = currentArea,
        )
    }
}
