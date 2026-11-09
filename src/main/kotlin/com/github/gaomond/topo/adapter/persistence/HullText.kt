package com.github.gaomond.topo.adapter.persistence

import com.github.gaomond.topo.domain.model.Coordinate

internal object HullText {
    fun read(wkt: String): List<Coordinate> {
        check(wkt.startsWith("POINT(") || wkt.startsWith("LINESTRING(") || wkt.startsWith("POLYGON(("))
        return wkt.substringAfter('(').replace("(", "").replace(")", "").split(',').map { pair ->
            val numbers = pair.trim().split(Regex("\\s+"))
            check(numbers.size == 2)
            Coordinate(numbers[1].toDouble(), numbers[0].toDouble())
        }
    }

    fun polygon(points: List<Coordinate>): String {
        check(points.size >= 4 && points.first() == points.last())
        return "POLYGON((" + points.joinToString(",") { "${it.lng} ${it.lat}" } + "))"
    }
}
