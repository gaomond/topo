package com.github.gaomond.topo.adapter.persistence

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
}
