package com.github.gaomond.topo.adapter.persistence.jpa

import com.github.gaomond.topo.domain.model.Game
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.GameSummary
import com.github.gaomond.topo.domain.port.GameRepositoryPort
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Game の永続化ポートを実装し、JPA のアクセスとドメインモデルへの変換を担う。 */
interface GameJpaRepository :
    Repository<GameRecord, UUID>,
    GameRepositoryPort {
    fun <S : GameRecord> save(record: S): S

    fun flush()

    @Query("select game from GameRecord game where game.id = :gameId")
    fun findRecordById(gameId: UUID): GameRecord?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select game from GameRecord game where game.id = :gameId")
    fun findRecordByIdWithLock(gameId: UUID): GameRecord?

    @Transactional
    override fun createGame(
        gameId: UUID,
        status: GameStatus,
        playerCount: Int,
        objectType: String,
        areaThreshold: Double,
    ) {
        save(
            GameRecord(
                id = gameId,
                status = status,
                playerCount = playerCount,
                objectType = objectType,
                areaThreshold = areaThreshold,
                creatorPlayerId = null,
            ),
        )
    }

    @Transactional
    override fun updateCreatorPlayerId(
        gameId: UUID,
        creatorPlayerId: UUID,
    ) {
        val record = findRecordById(gameId) ?: error("creatorPlayerId 更新対象の game が存在しません: $gameId")
        record.creatorPlayerId = creatorPlayerId
        save(record)
    }

    @Transactional
    override fun updateStatus(
        gameId: UUID,
        status: GameStatus,
    ) {
        val record = findRecordById(gameId) ?: error("status 更新対象の game が存在しません: $gameId")
        // 取得済みの他カラムと空間カラムを維持し、status のみを変更する。
        record.status = status
        save(record)
    }

    @Transactional(readOnly = true)
    override fun findSummary(gameId: UUID): GameSummary? = findRecordById(gameId)?.toSummary()

    @Transactional(propagation = Propagation.MANDATORY)
    override fun findByIdWithLock(gameId: UUID): Game? = findRecordByIdWithLock(gameId)?.toDomain()

    private fun GameRecord.toDomain(): Game = Game(id, status, playerCount, creatorPlayerId)

    private fun GameRecord.toSummary(): GameSummary =
        GameSummary(
            status = status,
            playerCount = playerCount,
            creatorPlayerId = creatorPlayerId,
            areaThreshold = areaThreshold,
        )
}
