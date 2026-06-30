package com.github.gaomond.topo.adapter.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * `player` の通常 CRUD リポジトリ（非空間カラム）。
 *
 * 確定座標（fixed_geom）を扱う空間クエリは US-13 以降に outbound の生 SQL で追加する。
 * Domain ポートは US-02 で導入する（GameRepository と同方針）。
 */
interface PlayerRepository : JpaRepository<PlayerJpaEntity, UUID>
