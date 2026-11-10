package com.github.gaomond.topo.adapter.persistence

import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.model.CurrentArea
import com.github.gaomond.topo.domain.port.CurrentAreaQueryPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/** SRID 4326の採用位置から、PostGISで凸包と測地面積（m²）を求める。 */
@Component
class CurrentAreaQueryAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : CurrentAreaQueryPort {
    private data class AreaRow(
        val selectedCount: Int,
        val inconsistent: Boolean,
        val sqm: Double,
        val coordinate: Coordinate?,
    )

    override fun currentArea(gameId: UUID): CurrentArea? {
        // READ COMMITTEDでも人数と凸包の点群がずれないよう、一文の読み取りで集計する。
        // LEFT LATERALにより空の点群でも集計行を残し、未計測より先に部分欠損を検出する。
        val rows =
            jdbcTemplate.query(
                """
                WITH selected AS (
                    SELECT
                        (fixed_geom IS NULL) <> (confirmed_at IS NULL) AS inconsistent,
                        CASE
                            WHEN fixed_geom IS NOT NULL AND confirmed_at IS NOT NULL THEN fixed_geom
                            WHEN fixed_geom IS NULL AND confirmed_at IS NULL
                                AND live_lat IS NOT NULL AND live_lng IS NOT NULL
                                THEN ST_SetSRID(ST_MakePoint(live_lng, live_lat), 4326)
                        END AS geom
                    FROM player WHERE game_id = ?
                ), hull AS (
                    SELECT COUNT(geom) AS selected_count,
                        COALESCE(BOOL_OR(inconsistent), false) AS inconsistent,
                        ST_ConvexHull(ST_Collect(geom)) AS h
                    FROM selected
                )
                SELECT selected_count, inconsistent, ST_Area(h::geography) AS sqm,
                    ST_Y(dp.geom) AS lat, ST_X(dp.geom) AS lng
                FROM hull LEFT JOIN LATERAL ST_DumpPoints(h) AS dp ON true
                ORDER BY dp.path
                """.trimIndent(),
                { rs, _ ->
                    val lat = rs.getObject("lat", Double::class.javaObjectType)
                    val lng = rs.getObject("lng", Double::class.javaObjectType)
                    AreaRow(
                        selectedCount = rs.getInt("selected_count"),
                        inconsistent = rs.getBoolean("inconsistent"),
                        sqm = rs.getDouble("sqm"),
                        coordinate = if (lat != null && lng != null) Coordinate(lat, lng) else null,
                    )
                },
                gameId,
            )
        val summary = rows.first()
        check(!summary.inconsistent) { "確定座標と確定時刻が揃っていません: gameId=$gameId" }
        if (summary.selectedCount < 3) return null
        val vertices = rows.mapNotNull { it.coordinate }
        check(vertices.isNotEmpty()) { "採用位置から凸包を取得できません: gameId=$gameId" }
        return CurrentArea(sqm = summary.sqm, hull = vertices)
    }
}
