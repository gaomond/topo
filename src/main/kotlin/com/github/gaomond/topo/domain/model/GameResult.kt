package com.github.gaomond.topo.domain.model

import java.util.Collections
import java.util.UUID

data class ConfirmationArea(
    val confirmedPlayerCount: Int,
    val areaSqm: Double,
    val hull: List<Coordinate>,
) {
    init {
        check(confirmedPlayerCount > 0)
        check(areaSqm.isFinite() && areaSqm >= 0)
        check(hull.isNotEmpty())
    }
}

class ValidatedConfirmationArea internal constructor(
    val gameId: UUID,
    val area: ConfirmationArea,
    val isFinal: Boolean,
)

class GameResult(
    val areaSqm: Double,
    val areaValid: Boolean,
    polygon: List<Coordinate>,
    objects: List<GameObjectSnapshot>,
) {
    val polygon: List<Coordinate> = Collections.unmodifiableList(ArrayList(polygon))
    val objects: List<GameObjectSnapshot> = Collections.unmodifiableList(objects.sortedBy { it.id })
    val objectCount: Int get() = objects.size

    init {
        check(
            this.objects
                .map { it.id }
                .toSet()
                .size == this.objects.size,
        ) { "獲得オブジェクトIDが重複しています" }
    }

    override fun equals(other: Any?): Boolean =
        other is GameResult &&
            areaSqm == other.areaSqm &&
            areaValid == other.areaValid &&
            polygon == other.polygon &&
            objects == other.objects

    override fun hashCode(): Int = listOf(areaSqm, areaValid, polygon, objects).hashCode()
}
