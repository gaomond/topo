package com.github.gaomond.topo.adapter.persistence.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * `player` テーブルの JPA エンティティ（非空間カラムのみ）。
 *
 * 空間カラム `fixed_geom`（Point,4326）はマッピングしない。確定座標は US-13 以降に
 * outbound の生 SQL（PostGIS）で扱う。`ddl-auto=validate` はエンティティに無いカラムを
 * 検証しないため、除外しても validate は通る。
 *
 * `game_id` は FK だが、本ストーリーでは @ManyToOne を張らず単純な UUID として保持する
 * （遅延ロードの罠を避け、参照解決は UseCase / 生 SQL に委ねる）。
 *
 * JPA エンティティのため data class は使わず通常 class とし、ID ベースの equals/hashCode を持つ。
 */
@Entity
@Table(name = "player")
class PlayerJpaEntity(
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
        if (other !is PlayerJpaEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()

    override fun toString(): String = "PlayerJpaEntity(id=$id, gameId=$gameId)"
}
