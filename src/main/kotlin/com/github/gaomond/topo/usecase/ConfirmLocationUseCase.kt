package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.exception.GameNotFoundException
import com.github.gaomond.topo.domain.exception.PlayerNotFoundException
import com.github.gaomond.topo.domain.port.GameRepositoryPort
import com.github.gaomond.topo.domain.port.HullServicePort
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
    private val hullService: HullServicePort,
) {
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun confirm(input: ConfirmLocationInput): ConfirmedLocationResult {
        val game = gameRepository.findByIdWithLock(input.gameId) ?: throw GameNotFoundException(input.gameId)
        val player =
            playerRepository.findByGameIdAndPlayerId(input.gameId, input.playerId)
                ?: throw PlayerNotFoundException(input.gameId, input.playerId)
        game.requireCanConfirm()

        // リクエストの座標をプレイヤーの確定位置に設定する。
        // 既に確定済みなら拒否する。DBへの保存は、後続の面積検証が通ってから行う。
        val playerWithConfirmedLocation = player.confirmLocation(input.coordinate, clock.instant())
        val confirmedLocation = checkNotNull(playerWithConfirmedLocation.confirmedLocation)

        // 今回の位置と他のプレイヤーの確定済み位置から、凸包と面積を求める。
        // 面積条件を満たす場合だけ、リクエストの座標aをDBへ保存する。
        val candidateArea = hullService.calculateArea(input.gameId, input.coordinate)
        val validatedArea = game.validateArea(candidateArea)

        playerRepository.confirmLocation(input.gameId, input.playerId, confirmedLocation)

        if (!validatedArea.isFinal) {
            return ConfirmedLocationResult(
                playerId = input.playerId,
                confirmed = true,
                confirmedAt = confirmedLocation.at,
            )
        }

        val objects = hullService.findCoveredObjects(game.objectType, validatedArea.area.hull)

        val completedGame = game.complete(validatedArea, objects)
        gameRepository.saveResult(completedGame)
        return ConfirmedLocationResult(
            playerId = input.playerId,
            confirmed = true,
            confirmedAt = confirmedLocation.at,
            result = completedGame.result,
        )
    }
}
