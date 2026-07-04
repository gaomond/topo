package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.GameNotFoundException
import com.github.gaomond.topo.domain.GameStartNotAllowedException
import com.github.gaomond.topo.domain.NotGameCreatorException
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.StartGameResult
import com.github.gaomond.topo.domain.port.GameRepositoryPort
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * ゲーム開始ユースケース（01-spec 1.1 のサーバー処理・US-06）。
 *
 * 存在確認 → creator 一致確認 → status==WAITING 確認 → 参加者数==playerCount 確認 →
 * status を ACTIVE に更新、を 1 トランザクションで実行する。
 *
 * 評価順は「権限（403）を状態・定員（409）より先」に固定する（リスク1 の確定）。
 * 非作成者に内部状態（開始可否）を漏らさないため、creator 判定を先に行う。
 *
 * 依存は Domain ポートのみで JPA 具象・PostGIS に依存しない（DIP / Clean Architecture）。
 */
@Service
class StartGameUseCase(
    private val gameRepository: GameRepositoryPort,
    private val playerRepository: PlayerRepositoryPort,
) {
    /**
     * ゲームを開始し、ACTIVE に遷移した結果を返す。
     *
     * @param gameId   開始対象のゲーム ID
     * @param playerId リクエスト元 playerId（creator 判定に使用）
     * @throws GameNotFoundException        gameId が存在しない（→404）
     * @throws NotGameCreatorException      リクエスト元が作成者でない（→403）
     * @throws GameStartNotAllowedException status が WAITING 以外 / 定員未達（→409）
     */
    @Transactional
    fun start(
        gameId: UUID,
        playerId: UUID,
    ): StartGameResult {
        val summary =
            gameRepository.findSummary(gameId)
                ?: throw GameNotFoundException(gameId)

        // 権限（403）を状態・定員（409）より先に評価する。
        if (summary.creatorPlayerId != playerId) {
            throw NotGameCreatorException(gameId)
        }

        if (summary.status != GameStatus.WAITING) {
            // ACTIVE / COMPLETED は開始不可。2 回目の開始はここで 409（冪等性）。
            throw GameStartNotAllowedException(
                gameId,
                GameStartNotAllowedException.Reason.NOT_WAITING,
            )
        }

        if (playerRepository.countByGameId(gameId) != summary.playerCount) {
            throw GameStartNotAllowedException(
                gameId,
                GameStartNotAllowedException.Reason.CAPACITY_NOT_REACHED,
            )
        }

        gameRepository.updateStatus(gameId, GameStatus.ACTIVE)
        return StartGameResult(gameId = gameId, status = GameStatus.ACTIVE)
    }
}
