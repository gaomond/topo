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
 * 参加と同じゲーム行をロックし、開始判定から状態保存まで同一トランザクションで行う。
 * 参加途中の人数を使わず、ロック取得後に確定した人数で開始を判断する。
 */
@Service
class StartGameUseCase(
    private val gameRepository: GameRepositoryPort,
    private val playerRepository: PlayerRepositoryPort,
) {
    /**
     * 作成者の操作でゲームを開始する。
     *
     * @throws GameNotFoundException 対象ゲームが存在しない。
     * @throws NotGameCreatorException 操作したプレイヤーが作成者ではない。
     * @throws GameStartNotAllowedException 待機中ではない、または参加者数が定員と一致しない。
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
