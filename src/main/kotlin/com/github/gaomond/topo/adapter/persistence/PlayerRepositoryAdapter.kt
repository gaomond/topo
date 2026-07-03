package com.github.gaomond.topo.adapter.persistence

import com.github.gaomond.topo.domain.model.PlayerView
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * [PlayerRepositoryPort]（Domain 抽象）を JPA リポジトリ [PlayerRepository] で実装する outbound アダプタ。
 *
 * ドメイン値 ↔ [PlayerJpaEntity] の変換をここに閉じ込める。displayName は UseCase で
 * フォールバック解決済み（常に非 null）で渡される。
 */
@Component
class PlayerRepositoryAdapter(
    private val playerRepository: PlayerRepository,
) : PlayerRepositoryPort {
    override fun createPlayer(
        playerId: UUID,
        gameId: UUID,
        displayName: String,
    ) {
        playerRepository.save(
            PlayerJpaEntity(
                id = playerId,
                gameId = gameId,
                displayName = displayName,
            ),
        )
    }

    override fun countByGameId(gameId: UUID): Int = playerRepository.countByGameId(gameId).toInt()

    override fun findByGameId(gameId: UUID): List<PlayerView> =
        playerRepository.findByGameIdOrderByJoinedAtAsc(gameId).map { entity ->
            PlayerView(
                playerId = entity.id,
                // displayName は参加/作成でフォールバック確定済みだが、DB 上は NULL 許容カラムのため
                // 念のため空文字にフォールバックして非 null を保証する。
                displayName = entity.displayName ?: "",
                confirmed = entity.confirmedAt != null,
            )
        }
}
