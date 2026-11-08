package com.github.gaomond.topo.usecase

import java.util.UUID

/** Controller から受け取る操作の入力。 */
data class UpdateLiveLocationInput(
    val gameId: UUID,
    val playerId: UUID,
    val lat: Double,
    val lng: Double,
)
