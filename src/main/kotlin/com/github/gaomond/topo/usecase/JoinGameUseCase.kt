package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.exception.GameJoinNotAllowedException
import com.github.gaomond.topo.domain.exception.GameNotFoundException
import com.github.gaomond.topo.domain.model.DisplayName
import com.github.gaomond.topo.domain.port.GameRepositoryPort
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 同じゲームへの参加・開始を、ゲーム行のロックで直列化する。
 * 判定から参加者保存まで同一トランザクションで行い、定員超過を防ぐ。
 */
@Service
class JoinGameUseCase(
    private val gameRepository: GameRepositoryPort,
    private val playerRepository: PlayerRepositoryPort,
) {
    /**
     * ゲームに参加する。
     *
     * @throws GameNotFoundException 対象ゲームが存在しない。
     * @throws GameJoinNotAllowedException 待機中ではない、または定員に達している。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun join(input: JoinGameInput): JoinGameResult {
        val game = gameRepository.findByIdWithLock(input.gameId) ?: throw GameNotFoundException(input.gameId)
        game.requireCanJoin { playerRepository.countByGameId(input.gameId) }

        val playerId = UUID.randomUUID()
        val displayName = DisplayName.resolve(input.displayName, playerId)
        playerRepository.createPlayer(
            playerId = playerId,
            gameId = input.gameId,
            displayName = displayName,
        )
        return JoinGameResult(playerId = playerId)
    }
}
