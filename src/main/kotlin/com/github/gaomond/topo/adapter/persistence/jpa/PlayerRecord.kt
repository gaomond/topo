package com.github.gaomond.topo.adapter.persistence.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * 空間カラム fixed_geom はマッピングせず、PostGIS の生 SQL で扱う。
 * エンティティにないカラムは ddl-auto=validate の検証対象外となる。
 *
 * game_id は UUID として保持し、関連エンティティの遅延ロードを発生させない。
 */
@Entity
@Table(name = "player")
class PlayerRecord(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID,
    @Column(name = "game_id", nullable = false)
    var gameId: UUID,
    @Column(name = "display_name")
    var displayName: String? = null,
    @Column(name = "joined_at", nullable = false)
    var joinedAt: Instant = Instant.now(),
    // ライブ位置（表示用途のみ。double で保持し空間型にはしない）
    @Column(name = "live_lat")
    var liveLat: Double? = null,
    @Column(name = "live_lng")
    var liveLng: Double? = null,
    @Column(name = "live_at")
    var liveAt: Instant? = null,
    // 確定時刻（確定前 NULL）。確定座標 fixed_geom はここに含めない。
    @Column(name = "confirmed_at")
    var confirmedAt: Instant? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlayerRecord) return false
        return id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()

    override fun toString(): String = "PlayerRecord(id=$id, gameId=$gameId)"
}
