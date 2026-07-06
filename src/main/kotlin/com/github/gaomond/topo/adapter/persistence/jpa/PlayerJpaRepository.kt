package com.github.gaomond.topo.adapter.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

/**
 * `player` の通常 CRUD リポジトリ（非空間カラム）。
 *
 * 確定座標（fixed_geom）を扱う空間クエリは US-13 以降に outbound の生 SQL で追加する。
 * Domain ポートは US-02 で導入する（GameJpaRepository と同方針）。
 */
interface PlayerJpaRepository : JpaRepository<PlayerJpaEntity, UUID> {
    /** 指定ゲームの参加者数（定員チェック用）。 */
    fun countByGameId(gameId: UUID): Long

    /** 指定ゲームの参加者一覧を参加順（joined_at 昇順）で返す。 */
    fun findByGameIdOrderByJoinedAtAsc(gameId: UUID): List<PlayerJpaEntity>

    /**
     * ライブ位置（live_lat / live_lng / live_at）を一致つきで更新する（US-07）。
     *
     * `id = playerId AND game_id = gameId` を条件にし、所属不一致・不在を DB レベルで弾く。
     * live_* は非空間カラム（DOUBLE PRECISION / TIMESTAMPTZ）のため JPQL で完結（生 SQL 不要）。
     * 更新のみで同一 Tx 内に読み戻さないため clearAutomatically は付けない。
     *
     * @return 更新行数（0 = 不在 / 非所属 → 404 の材料、1 = 更新成功）
     */
    @Modifying
    @Query(
        "update PlayerJpaEntity p set p.liveLat = :lat, p.liveLng = :lng, p.liveAt = :at " +
            "where p.id = :playerId and p.gameId = :gameId",
    )
    fun updateLiveLocation(
        @Param("gameId") gameId: UUID,
        @Param("playerId") playerId: UUID,
        @Param("lat") lat: Double,
        @Param("lng") lng: Double,
        @Param("at") at: Instant,
    ): Int
}
