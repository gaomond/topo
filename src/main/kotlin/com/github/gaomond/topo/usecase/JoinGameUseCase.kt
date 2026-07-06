package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.exception.GameJoinNotAllowedException
import com.github.gaomond.topo.domain.exception.GameNotFoundException
import com.github.gaomond.topo.domain.model.DisplayName
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.JoinGameCommand
import com.github.gaomond.topo.domain.model.JoinGameResult
import com.github.gaomond.topo.domain.port.GameRepositoryPort
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * ゲーム参加ユースケース（01-spec 1.1 のサーバー処理）。
 *
 * 存在確認 → status==WAITING 判定 → 参加者数 < playerCount 判定 → player INSERT
 * （playerId 発番・displayName フォールバック確定・game_id・joined_at）を 1 トランザクションで実行する。
 *
 * 依存は Domain ポートのみで JPA 具象・PostGIS に依存しない（DIP / Clean Architecture）。
 * 同時参加の競合（定員 off-by-one）は MVP では厳密制御しない（`@Transactional` 内チェックで許容）。
 */
@Service
class JoinGameUseCase(
    private val gameRepository: GameRepositoryPort,
    private val playerRepository: PlayerRepositoryPort,
) {
    /**
     * ゲームに参加し、発番した playerId を返す。
     *
     * @throws GameNotFoundException       gameId が存在しない（→404）
     * @throws GameJoinNotAllowedException status が WAITING 以外 / 定員到達（→409）
     */
    @Transactional
    fun join(command: JoinGameCommand): JoinGameResult {
        val summary =
            gameRepository.findSummary(command.gameId)
                ?: throw GameNotFoundException(command.gameId)

        if (summary.status != GameStatus.WAITING) {
            throw GameJoinNotAllowedException(
                command.gameId,
                GameJoinNotAllowedException.Reason.NOT_WAITING,
            )
        }

        if (playerRepository.countByGameId(command.gameId) >= summary.playerCount) {
            throw GameJoinNotAllowedException(
                command.gameId,
                GameJoinNotAllowedException.Reason.CAPACITY_REACHED,
            )
        }

        val playerId = UUID.randomUUID()
        val displayName = DisplayName.resolve(command.displayName, playerId)
        playerRepository.createPlayer(
            playerId = playerId,
            gameId = command.gameId,
            displayName = displayName,
        )
        return JoinGameResult(playerId = playerId)
    }
}
