package com.github.gaomond.topo.domain.port

import com.github.gaomond.topo.domain.model.CurrentArea
import java.util.UUID

/**
 * live 位置の暫定凸包面積を求める空間計算ポート（Domain 抽象・US-08）。
 *
 * 凸包・面積の計算はサーバー（PostGIS）に寄せる（DRY・改ざん防止）。UseCase はこのポートに依存し、
 * PostGIS / 生 SQL を知らない。生 SQL（`ST_ConvexHull` + `ST_Area(::geography)`）は実装（outbound）に
 * 閉じ込める（DIP / Clean Architecture）。
 */
interface LiveAreaQueryPort {
    /**
     * 指定ゲームの live 位置から暫定凸包の [CurrentArea] を求める。
     *
     * - 対象点は `live_lat` を持つ全参加者（online で絞らない・E1）。
     * - **live 点が 3 点未満なら null**（多角形が成立せず「未計測」を明示・E2）。
     * - 退化（一直線・重複で面積ゼロ）は `sqm = 0` + 成立する頂点列を返す（null にしない）。
     * - 面積は `ST_Area(::geography)` の測地 m²。[CurrentArea.hull] は凸包頂点の順序付き閉環。
     *
     * @param gameId 対象ゲーム ID
     * @return 凸包の面積・頂点列。live 点 < 3 のときは null。
     */
    fun currentLiveArea(gameId: UUID): CurrentArea?
}
