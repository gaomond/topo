package com.github.gaomond.topo.domain.model

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

data class GameResult(
    val areaSqm: Double,
    val areaValid: Boolean,
    val objectCount: Int,
    val polygon: List<Coordinate>,
)
