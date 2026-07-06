package com.github.gaomond.topo.adapter.persistence

import com.github.gaomond.topo.adapter.persistence.jpa.PlayerJpaEntity
import com.github.gaomond.topo.adapter.persistence.jpa.PlayerJpaRepository
import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.model.PlayerSnapshot
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * [PlayerRepositoryPort]（Domain 抽象）を JPA リポジトリ [PlayerJpaRepository] で実装する outbound アダプタ。
 *
 * ドメイン値 ↔ [PlayerJpaEntity] の変換をここに閉じ込める。displayName は UseCase で
 * フォールバック解決済み（常に非 null）で渡される。
 */
@Component
class PlayerRepositoryAdapter(
    private val playerRepository: PlayerJpaRepository,
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

    override fun updateLiveLocation(
        gameId: UUID,
        playerId: UUID,
        coordinate: Coordinate,
        at: Instant,
    ): Boolean =
        // Domain 値（Coordinate）↔ JPA の分解をアダプタ内に閉じ込める。更新行数 > 0 を Boolean で返す。
        playerRepository.updateLiveLocation(
            gameId = gameId,
            playerId = playerId,
            lat = coordinate.lat,
            lng = coordinate.lng,
            at = at,
        ) > 0

    override fun findByGameId(gameId: UUID): List<PlayerSnapshot> =
        playerRepository.findByGameIdOrderByJoinedAtAsc(gameId).map { entity ->
            PlayerSnapshot(
                playerId = entity.id,
                // displayName は参加/作成でフォールバック確定済みだが、DB 上は NULL 許容カラムのため
                // 念のため空文字にフォールバックして非 null を保証する。
                displayName = entity.displayName ?: "",
                confirmed = entity.confirmedAt != null,
            )
        }
}
