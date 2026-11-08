package com.github.gaomond.topo.usecase

import java.util.UUID

data class UpdateLiveLocationInput(
    val gameId: UUID,
    val playerId: UUID,
    val lat: Double,
    val lng: Double,
)
