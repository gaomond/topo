package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.model.GameResult
import io.swagger.v3.oas.annotations.media.Schema

@Schema(requiredProperties = ["areaSqm", "areaValid", "objectCount", "polygon"])
data class GameResultPayload(
    val areaSqm: Double,
    val areaValid: Boolean,
    val objectCount: Int,
    val polygon: List<CoordinatePayload>,
) {
    companion object {
        fun from(result: GameResult) =
            GameResultPayload(
                result.areaSqm,
                result.areaValid,
                result.objectCount,
                result.polygon.map { CoordinatePayload(it.lat, it.lng) },
            )
    }
}

@Schema(requiredProperties = ["lat", "lng"])
data class CoordinatePayload(
    val lat: Double,
    val lng: Double,
)
