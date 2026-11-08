package com.github.gaomond.topo.adapter.persistence.jpa

import com.github.gaomond.topo.domain.model.Game
import com.github.gaomond.topo.domain.model.GameSummary
import com.github.gaomond.topo.domain.model.ObjectType
import com.github.gaomond.topo.domain.port.GameRepositoryPort
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

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
    override fun createWithoutCreator(game: Game) {
        save(
            GameRecord(
                id = game.gameId,
                status = game.status,
                playerCount = game.playerCount,
                objectType = game.objectType.jsonValue,
                areaThreshold = game.areaThreshold,
                creatorPlayerId = null,
            ),
        )
    }

    @Transactional
    override fun updateCreatorPlayerId(game: Game) {
        val creatorPlayerId = checkNotNull(game.creatorPlayerId) { "作成者IDが設定されていません: ${game.gameId}" }
        val gameId = game.gameId
        val record = findRecordById(gameId) ?: error("creatorPlayerId 更新対象の game が存在しません: $gameId")
        record.creatorPlayerId = creatorPlayerId
        save(record)
    }

    @Transactional
    override fun updateStatus(game: Game) {
        val gameId = game.gameId
        val record = findRecordById(gameId) ?: error("status 更新対象の game が存在しません: $gameId")
        // 取得済みの他カラムと空間カラムを維持し、status のみを変更する。
        record.status = game.status
        save(record)
    }

    @Transactional(readOnly = true)
    override fun findSummary(gameId: UUID): GameSummary? = findRecordById(gameId)?.toSummary()

    @Transactional(propagation = Propagation.MANDATORY)
    override fun findByIdWithLock(gameId: UUID): Game? = findRecordByIdWithLock(gameId)?.toDomain()

    private fun GameRecord.toDomain(): Game =
        Game.restore(
            id,
            status,
            playerCount,
            creatorPlayerId,
            ObjectType.entries.single { it.jsonValue == objectType },
            areaThreshold,
        )

    private fun GameRecord.toSummary(): GameSummary =
        GameSummary(
            status = status,
            playerCount = playerCount,
            creatorPlayerId = creatorPlayerId,
            areaThreshold = areaThreshold,
        )
}
