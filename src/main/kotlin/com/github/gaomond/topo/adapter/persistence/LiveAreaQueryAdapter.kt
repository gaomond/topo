package com.github.gaomond.topo.adapter.persistence

import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.model.CurrentArea
import com.github.gaomond.topo.domain.port.LiveAreaQueryPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * [LiveAreaQueryPort] を PostGIS 生 SQL で実装する outbound アダプタ（US-08 で空間 SQL を初導入）。
 *
 * `ST_ConvexHull(ST_Collect(live points))` の測地面積（`ST_Area(::geography)`）と凸包頂点列を求める。
 * PostGIS / 生 SQL 依存は本ファイルに閉じ込め、Domain / UseCase へは Domain 値 [CurrentArea] だけを返す
 * （ORM 方針: 空間クエリは outbound の生 SQL に隔離）。
 *
 * 経度/緯度の取り違え防止: 点生成は `ST_MakePoint(lng, lat)`（X=lng, Y=lat）、頂点抽出は
 * `ST_X = lng` / `ST_Y = lat` で対応を明示する。
 */
@Component
class LiveAreaQueryAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : LiveAreaQueryPort {
    private data class HullVertex(
        val sqm: Double,
        val lat: Double,
        val lng: Double,
    )

    override fun currentLiveArea(gameId: UUID): CurrentArea? {
        // live 点 < 3 は多角形が成立しないため null（E2）。退化の sqm=0 と混同しないよう件数で先にガードする。
        val liveCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM player
                WHERE game_id = ? AND live_lat IS NOT NULL AND live_lng IS NOT NULL
                """.trimIndent(),
                Int::class.java,
                gameId,
            ) ?: 0
        if (liveCount < 3) return null

        // 凸包を作り、測地面積と頂点列（ST_DumpPoints を path 順）を 1 クエリで取得する。
        // 面積は各行同一値（hull と DumpPoints の直積）なので先頭行から読む。
        // 退化（一直線/重複）でも ConvexHull は LineString/Point を返し、ST_Area(::geography)=0 + 成立頂点列になる。
        val vertices =
            jdbcTemplate.query(
                """
                WITH pts AS (
                    SELECT ST_SetSRID(ST_MakePoint(live_lng, live_lat), 4326) AS geom
                    FROM player
                    WHERE game_id = ? AND live_lat IS NOT NULL AND live_lng IS NOT NULL
                ),
                hull AS (
                    SELECT ST_ConvexHull(ST_Collect(geom)) AS h FROM pts
                )
                SELECT
                    ST_Area(h::geography) AS sqm,
                    ST_Y((dp).geom) AS lat,
                    ST_X((dp).geom) AS lng
                FROM hull, LATERAL ST_DumpPoints(h) AS dp
                ORDER BY (dp).path
                """.trimIndent(),
                { rs, _ ->
                    HullVertex(
                        sqm = rs.getDouble("sqm"),
                        lat = rs.getDouble("lat"),
                        lng = rs.getDouble("lng"),
                    )
                },
                gameId,
            )
        if (vertices.isEmpty()) return null

        return CurrentArea(
            sqm = vertices.first().sqm,
            hull = vertices.map { Coordinate(lat = it.lat, lng = it.lng) },
        )
    }
}
