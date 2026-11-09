package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.model.GameObjectSnapshot
import com.github.gaomond.topo.domain.model.GameResult
import io.swagger.v3.oas.annotations.media.Schema

@Schema(requiredProperties = ["areaSqm", "areaValid", "objectCount", "polygon", "objects"])
data class GameResultPayload(
    val areaSqm: Double,
    val areaValid: Boolean,
    val objectCount: Int,
    val polygon: List<CoordinatePayload>,
    val objects: List<GameObjectSnapshotPayload>,
) {
    companion object {
        fun from(result: GameResult) =
            GameResultPayload(
                result.areaSqm,
                result.areaValid,
                result.objectCount,
                result.polygon.map { CoordinatePayload(it.lat, it.lng) },
                result.objects.map { GameObjectSnapshotPayload.from(it) },
            )
    }
}

@Schema(requiredProperties = ["lat", "lng"])
data class CoordinatePayload(
    val lat: Double,
    val lng: Double,
)

@Schema(requiredProperties = ["id", "objectType", "name", "lat", "lng"])
data class GameObjectSnapshotPayload(
    val id: String,
    val objectType: String,
    @field:Schema(nullable = true, types = ["string", "null"])
    val name: String?,
    val lat: Double,
    val lng: Double,
) {
    companion object {
        fun from(snapshot: GameObjectSnapshot) =
            GameObjectSnapshotPayload(
                snapshot.id.toString(),
                snapshot.objectType.jsonValue,
                snapshot.name,
                snapshot.coordinate.lat,
                snapshot.coordinate.lng,
            )
    }
}
