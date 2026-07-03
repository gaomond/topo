package com.github.gaomond.topo.adapter.persistence

import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.GameSummary
import com.github.gaomond.topo.domain.port.GameRepositoryPort
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * [GameRepositoryPort]（Domain 抽象）を JPA リポジトリ [GameRepository] で実装する outbound アダプタ。
 *
 * ドメイン値 ↔ [GameJpaEntity] の変換をここに閉じ込め、UseCase から JPA を隠す。
 * 本ストーリーは非空間 CRUD のみで生 SQL は不要（空間クエリは US-13 以降）。
 */
@Component
class GameRepositoryAdapter(
    private val gameRepository: GameRepository,
) : GameRepositoryPort {
    override fun createGame(
        gameId: UUID,
        status: GameStatus,
        playerCount: Int,
        objectType: String,
        areaThreshold: Double,
    ) {
        gameRepository.save(
            GameJpaEntity(
                id = gameId,
                status = status,
                playerCount = playerCount,
                objectType = objectType,
                areaThreshold = areaThreshold,
                // creatorPlayerId は後段 UPDATE で埋める。結果カラムは NULL のまま。
                creatorPlayerId = null,
            ),
        )
    }

    override fun updateCreatorPlayerId(
        gameId: UUID,
        creatorPlayerId: UUID,
    ) {
        val game =
            gameRepository.findById(gameId).orElseThrow {
                IllegalStateException("creatorPlayerId 更新対象の game が存在しません: $gameId")
            }
        game.creatorPlayerId = creatorPlayerId
        gameRepository.save(game)
    }

    override fun findSummary(gameId: UUID): GameSummary? =
        gameRepository
            .findById(gameId)
            .map { GameSummary(status = it.status, playerCount = it.playerCount) }
            .orElse(null)
}
