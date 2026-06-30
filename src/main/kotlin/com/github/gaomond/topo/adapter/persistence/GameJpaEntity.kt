package com.github.gaomond.topo.adapter.persistence

import com.github.gaomond.topo.domain.model.GameStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * `game` テーブルの JPA エンティティ（非空間カラムのみ）。
 *
 * 空間カラム `polygon_geom`（Polygon,4326）はマッピングしない。地理空間は US-13 以降に
 * outbound の生 SQL（PostGIS）で扱う方針（CLAUDE.md「PostGIS を Domain/UseCase から隠す」）。
 * `ddl-auto=validate` はエンティティに無いカラムを検証しないため、除外しても validate は通る。
 *
 * JPA エンティティのため data class は使わず通常 class とし、ID ベースの equals/hashCode を持つ。
 * UUID 主キーはクライアント発番（コンストラクタで確定）であり DB 生成ではないため、id は非 null。
 */
@Entity
@Table(name = "game")
class GameJpaEntity(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID,
    // DB enum 型 game_status と対応づける。EnumType.STRING 単独では Hibernate が
    // varchar として送り型不一致になり得るため、PostgreSQL named enum 対応の
    // SqlTypes.NAMED_ENUM を指定する。
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "game_status")
    var status: GameStatus,
    @Column(name = "player_count", nullable = false)
    var playerCount: Int,
    @Column(name = "object_type", nullable = false)
    var objectType: String,
    @Column(name = "area_threshold", nullable = false)
    var areaThreshold: Double,
    // 作成者の player.id。循環回避のため NULL 許容。
    @Column(name = "creator_player_id")
    var creatorPlayerId: UUID? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    // 結果カラム（COMPLETED まで NULL）。空間の polygon_geom はここに含めない。
    @Column(name = "area_sqm")
    var areaSqm: Double? = null,
    @Column(name = "area_valid")
    var areaValid: Boolean? = null,
    @Column(name = "object_count")
    var objectCount: Int? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GameJpaEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()

    override fun toString(): String = "GameJpaEntity(id=$id, status=$status)"
}
