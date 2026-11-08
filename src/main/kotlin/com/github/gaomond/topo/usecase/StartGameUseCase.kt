package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.exception.GameNotFoundException
import com.github.gaomond.topo.domain.exception.GameStartNotAllowedException
import com.github.gaomond.topo.domain.exception.NotGameCreatorException
import com.github.gaomond.topo.domain.port.GameRepositoryPort
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

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
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun start(input: StartGameInput): StartGameResult {
        val gameId = input.gameId
        val playerId = input.playerId
        val game = gameRepository.findByIdWithLock(gameId) ?: throw GameNotFoundException(gameId)
        val startedGame = game.start(playerId) { playerRepository.countByGameId(gameId) }

        gameRepository.updateStatus(gameId, startedGame.status)
        return StartGameResult(gameId = gameId, status = startedGame.status)
    }
}
