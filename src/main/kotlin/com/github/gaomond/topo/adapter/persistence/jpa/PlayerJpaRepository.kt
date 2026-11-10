package com.github.gaomond.topo.adapter.persistence.jpa

import com.github.gaomond.topo.domain.model.ConfirmedLocation
import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.model.LiveLocation
import com.github.gaomond.topo.domain.model.Player
import com.github.gaomond.topo.domain.model.PlayerReading
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface PlayerJpaRepository :
    Repository<PlayerRecord, UUID>,
    PlayerRepositoryPort {
    fun <S : PlayerRecord> save(record: S): S

    fun flush()

    @Query("select player from PlayerRecord player where player.id = :playerId")
    fun findRecordById(playerId: UUID): PlayerRecord?

    fun findRecordByIdAndGameId(
        id: UUID,
        gameId: UUID,
    ): PlayerRecord?

    @Query(
        value = "select ST_Y(fixed_geom) as lat, ST_X(fixed_geom) as lng from player where id = :playerId and game_id = :gameId",
        nativeQuery = true,
    )
    fun findFixedCoordinate(
        gameId: UUID,
        playerId: UUID,
    ): FixedCoordinateRecord?

    // 生SQLで更新した確定時刻を、同一トランザクション内の再取得にも反映する。
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value =
            "update player set fixed_geom = ST_SetSRID(ST_MakePoint(:lng, :lat), 4326), confirmed_at = :at " +
                "where id = :playerId and game_id = :gameId",
        nativeQuery = true,
    )
    fun confirmRecordLocation(
        gameId: UUID,
        playerId: UUID,
        lat: Double,
        lng: Double,
        at: Instant,
    ): Int

    @Transactional(readOnly = true)
    override fun findByGameIdAndPlayerId(
        gameId: UUID,
        playerId: UUID,
    ): Player? {
        val record = findRecordByIdAndGameId(playerId, gameId) ?: return null
        val location =
            record.confirmedAt?.let { at ->
                val fixed = checkNotNull(findFixedCoordinate(gameId, playerId))
                ConfirmedLocation(Coordinate(checkNotNull(fixed.lat), checkNotNull(fixed.lng)), at)
            }
        return Player.restore(record.id, record.gameId, record.displayName ?: "", location)
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun confirmLocation(
        gameId: UUID,
        playerId: UUID,
        location: ConfirmedLocation,
    ) {
        check(confirmRecordLocation(gameId, playerId, location.coordinate.lat, location.coordinate.lng, location.at) == 1) {
            "確定位置の保存対象が存在しません: gameId=$gameId, playerId=$playerId"
        }
    }

    @Query("select count(player) from PlayerRecord player where player.gameId = :gameId")
    fun countRecordsByGameId(gameId: UUID): Long

    @Query(
        value = """
            select id, display_name as "displayName", live_lat as "liveLat", live_lng as "liveLng",
                   live_at as "liveAt", ST_Y(fixed_geom) as "fixedLat", ST_X(fixed_geom) as "fixedLng",
                   confirmed_at as "confirmedAt"
            from player where game_id = :gameId order by joined_at asc
        """,
        nativeQuery = true,
    )
    fun findReadingsByGameId(gameId: UUID): List<PlayerReadingRecord>

    @Modifying
    @Query(
        "update PlayerRecord p set p.liveLat = :lat, p.liveLng = :lng, p.liveAt = :at " +
            "where p.id = :playerId and p.gameId = :gameId",
    )
    fun updateLiveRecordLocation(
        gameId: UUID,
        playerId: UUID,
        lat: Double,
        lng: Double,
        at: Instant,
    ): Int

    @Transactional
    override fun createPlayer(player: Player) {
        save(PlayerRecord(id = player.playerId, gameId = player.gameId, displayName = player.displayName))
    }

    @Transactional(readOnly = true)
    override fun countByGameId(gameId: UUID): Int = countRecordsByGameId(gameId).toInt()

    @Transactional
    override fun updateLiveLocation(
        gameId: UUID,
        playerId: UUID,
        coordinate: Coordinate,
        at: Instant,
    ): Boolean = updateLiveRecordLocation(gameId, playerId, coordinate.lat, coordinate.lng, at) > 0

    @Transactional(readOnly = true)
    override fun findByGameId(gameId: UUID): List<PlayerReading> =
        findReadingsByGameId(gameId).map { record ->
            PlayerReading(
                playerId = record.id,
                displayName = record.displayName ?: "",
                confirmedLocation = record.toConfirmedLocation(),
                live = record.toLiveLocation(),
            )
        }

    private fun PlayerReadingRecord.toConfirmedLocation(): ConfirmedLocation? {
        val lat = fixedLat
        val lng = fixedLng
        val at = confirmedAt
        if (lat == null && lng == null && at == null) return null
        check(lat != null && lng != null && at != null) { "保存済みの確定記録が不完全です: playerId=$id" }
        return ConfirmedLocation(Coordinate(lat, lng), at)
    }

    private fun PlayerReadingRecord.toLiveLocation(): LiveLocation? {
        val lat = liveLat
        val lng = liveLng
        val at = liveAt
        return if (lat != null && lng != null && at != null) {
            LiveLocation(Coordinate(lat = lat, lng = lng), at)
        } else {
            null
        }
    }
}

interface FixedCoordinateRecord {
    val lat: Double?
    val lng: Double?
}

interface PlayerReadingRecord {
    val id: UUID
    val displayName: String?
    val liveLat: Double?
    val liveLng: Double?
    val liveAt: Instant?
    val fixedLat: Double?
    val fixedLng: Double?
    val confirmedAt: Instant?
}
