package com.github.gaomond.topo.adapter.persistence.jpa

import com.github.gaomond.topo.adapter.persistence.HullText
import com.github.gaomond.topo.adapter.persistence.ResultObjectsJson
import com.github.gaomond.topo.domain.model.Game
import com.github.gaomond.topo.domain.model.GameResult
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.GameSummary
import com.github.gaomond.topo.domain.model.ObjectType
import com.github.gaomond.topo.domain.port.GameRepositoryPort
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
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

    @Query(
        value = """
        SELECT status::text AS status, player_count AS "playerCount", creator_player_id AS "creatorPlayerId",
               area_threshold AS "areaThreshold", area_sqm AS "areaSqm", area_valid AS "areaValid",
               object_count AS "objectCount", ST_AsText(polygon_geom,17) AS polygon,
               object_type AS "objectType", result_objects::text AS "resultObjects"
        FROM game WHERE id = :gameId
    """,
        nativeQuery = true,
    )
    fun findSummaryRecord(gameId: UUID): GameSummaryRecord?

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        UPDATE game SET status = 'COMPLETED', polygon_geom = ST_GeomFromText(:polygon,4326),
            area_sqm = :areaSqm, area_valid = true, object_count = :objectCount,
            result_objects = CAST(:resultObjects AS jsonb) WHERE id = :gameId
    """,
        nativeQuery = true,
    )
    fun updateResultRecord(
        gameId: UUID,
        polygon: String,
        areaSqm: Double,
        objectCount: Int,
        resultObjects: String,
    ): Int

    @Transactional(propagation = Propagation.MANDATORY)
    override fun saveResult(game: Game) {
        check(game.status == GameStatus.COMPLETED)
        val result = checkNotNull(game.result)
        check(result.areaValid)
        check(
            updateResultRecord(
                game.gameId,
                HullText.polygon(result.polygon),
                result.areaSqm,
                result.objectCount,
                ResultObjectsJson.write(result.objects),
            ) == 1,
        )
    }

    @Transactional(readOnly = true)
    override fun findSummary(gameId: UUID): GameSummary? =
        findSummaryRecord(gameId)?.let { row ->
            val status = GameStatus.valueOf(row.status)
            GameSummary(
                status,
                row.playerCount,
                row.creatorPlayerId,
                row.areaThreshold,
                if (status == GameStatus.COMPLETED) {
                    val objects = ResultObjectsJson.read(checkNotNull(row.resultObjects))
                    check(objects.all { it.objectType.jsonValue == row.objectType }) { "保存結果の対象種別が一致しません" }
                    GameResult(
                        checkNotNull(row.areaSqm),
                        checkNotNull(row.areaValid),
                        HullText.read(checkNotNull(row.polygon)),
                        objects,
                    ).also { check(it.objectCount == row.objectCount) { "保存結果の件数と明細が一致しません" } }
                } else {
                    null
                },
            )
        }

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
}

interface GameSummaryRecord {
    val status: String
    val playerCount: Int
    val creatorPlayerId: UUID?
    val areaThreshold: Double
    val areaSqm: Double?
    val areaValid: Boolean?
    val objectCount: Int?
    val objectType: String
    val resultObjects: String?
    val polygon: String?
}
