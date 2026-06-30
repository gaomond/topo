package com.github.gaomond.topo.adapter.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * `game` の通常 CRUD リポジトリ（非空間カラム）。
 *
 * 空間クエリ（凸包・測地面積・範囲内 COUNT）は US-13 以降に outbound の生 SQL で追加する。
 * Domain ポート（リポジトリ抽象）は UseCase が登場する US-02 で導入し、本リポジトリを
 * 実装に降格させる方針（現段階では YAGNI のため抽象を前倒ししない）。
 */
interface GameRepository : JpaRepository<GameJpaEntity, UUID>
