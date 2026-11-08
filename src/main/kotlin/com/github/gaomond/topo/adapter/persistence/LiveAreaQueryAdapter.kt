package com.github.gaomond.topo.adapter.persistence

import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.model.CurrentArea
import com.github.gaomond.topo.domain.port.LiveAreaQueryPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * SRID 4326 のライブ座標から凸包と測地面積（m²）を求める。
 *
 * 点生成は ST_MakePoint(lng, lat)（X=経度、Y=緯度）とし、頂点抽出も同じ対応を使う。
 * [CurrentArea] の頂点列は ST_DumpPoints の path 順に返す。多角形では閉環し、
 * 一直線・重複による退化では線分または点となり、測地面積は 0 となる。
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
        // 3 点未満の未計測と、退化による面積 0 を区別する。
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

        // 面積は hull と頂点列の直積で各行に重複するため、先頭行の値を使う。
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
