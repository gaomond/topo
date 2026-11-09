package com.github.gaomond.topo.adapter.persistence

import com.github.gaomond.topo.domain.model.ConfirmationArea
import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.model.GameObjectSnapshot
import com.github.gaomond.topo.domain.model.ObjectType
import com.github.gaomond.topo.domain.port.HullServicePort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PostgisHullService(
    private val jdbc: JdbcTemplate,
) : HullServicePort {
    override fun calculateArea(
        gameId: UUID,
        candidate: Coordinate,
    ): ConfirmationArea =
        checkNotNull(
            jdbc.queryForObject(
                """
                WITH points AS (
                    SELECT pl.fixed_geom AS point_geom
                    FROM player AS pl
                    WHERE pl.game_id = ? AND pl.confirmed_at IS NOT NULL
                    UNION ALL
                    SELECT ST_SetSRID(ST_MakePoint(?, ?), 4326) AS point_geom
                ), hull AS MATERIALIZED (
                    SELECT
                        COUNT(*) AS player_count,
                        ST_ConvexHull(ST_Collect(po.point_geom)) AS hull_geom
                    FROM points AS po
                )
                SELECT
                    h.player_count,
                    ST_Area(h.hull_geom::geography) AS area_sqm,
                    ST_AsText(h.hull_geom, 17) AS hull_wkt
                FROM hull AS h
                """.trimIndent(),
                { rs, _ ->
                    ConfirmationArea(
                        confirmedPlayerCount = rs.getInt("player_count"),
                        areaSqm = rs.getDouble("area_sqm"),
                        hull = HullText.read(checkNotNull(rs.getString("hull_wkt"))),
                    )
                },
                gameId,
                candidate.lng,
                candidate.lat,
            ),
        )

    override fun findCoveredObjects(
        objectType: ObjectType,
        hull: List<Coordinate>,
    ): List<GameObjectSnapshot> =
        jdbc.query(
            """
            SELECT obj.id, obj.object_type, obj.name, ST_Y(obj.geom) AS lat, ST_X(obj.geom) AS lng
            FROM game_object AS obj
            WHERE obj.object_type = ?
              AND ST_Covers(ST_GeomFromText(?, 4326), obj.geom)
            ORDER BY obj.id
            """.trimIndent(),
            { rs, _ ->
                GameObjectSnapshot(
                    rs.getLong("id"),
                    ObjectType.entries.single { it.jsonValue == rs.getString("object_type") },
                    rs.getString("name"),
                    Coordinate(rs.getDouble("lat"), rs.getDouble("lng")),
                )
            },
            objectType.jsonValue,
            HullText.polygon(hull),
        )
}
