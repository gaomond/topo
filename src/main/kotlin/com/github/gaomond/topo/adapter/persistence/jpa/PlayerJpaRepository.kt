package com.github.gaomond.topo.adapter.persistence.jpa

import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.model.LiveLocation
import com.github.gaomond.topo.domain.model.Player
import com.github.gaomond.topo.domain.model.PlayerReading
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
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

    @Query("select count(player) from PlayerRecord player where player.gameId = :gameId")
    fun countRecordsByGameId(gameId: UUID): Long

    fun findRecordsByGameIdOrderByJoinedAtAsc(gameId: UUID): List<PlayerRecord>

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
        findRecordsByGameIdOrderByJoinedAtAsc(gameId).map { record ->
            PlayerReading(
                playerId = record.id,
                displayName = record.displayName ?: "",
                confirmed = record.confirmedAt != null,
                live = record.toLiveLocation(),
            )
        }

    private fun PlayerRecord.toLiveLocation(): LiveLocation? {
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
