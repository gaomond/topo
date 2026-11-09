package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.exception.GameNotFoundException
import com.github.gaomond.topo.domain.exception.PlayerNotFoundException
import com.github.gaomond.topo.domain.port.GameRepositoryPort
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class ConfirmLocationUseCase(
    private val gameRepository: GameRepositoryPort,
    private val playerRepository: PlayerRepositoryPort,
    private val clock: Clock,
) {
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun confirm(input: ConfirmLocationInput): ConfirmLocationResult {
        val game = gameRepository.findByIdWithLock(input.gameId) ?: throw GameNotFoundException(input.gameId)
        val player =
            playerRepository.findByGameIdAndPlayerId(input.gameId, input.playerId)
                ?: throw PlayerNotFoundException(input.gameId, input.playerId)
        game.requireCanConfirm()
        val confirmed = player.confirmLocation(input.coordinate, clock.instant())
        val location = checkNotNull(confirmed.confirmedLocation)
        playerRepository.confirmLocation(input.gameId, input.playerId, location)
        return ConfirmLocationResult(input.playerId, true, location.at)
    }
}
