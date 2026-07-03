package com.github.gaomond.topo.adapter.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
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
}
