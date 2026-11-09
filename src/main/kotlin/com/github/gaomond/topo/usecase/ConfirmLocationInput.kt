package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.model.Coordinate
import java.util.UUID

data class ConfirmLocationInput(
    val gameId: UUID,
    val playerId: UUID,
    val coordinate: Coordinate,
)
