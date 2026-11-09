package com.github.gaomond.topo.domain.model

import com.github.gaomond.topo.domain.exception.PlayerAlreadyConfirmedException
import java.time.Instant
import java.util.UUID

data class ConfirmedLocation(
    val coordinate: Coordinate,
    val at: Instant,
)

class Player private constructor(
    val playerId: UUID,
    val gameId: UUID,
    val displayName: String,
    val confirmedLocation: ConfirmedLocation?,
) {
    fun confirmLocation(
        coordinate: Coordinate,
        at: Instant,
    ): Player {
        if (confirmedLocation != null) throw PlayerAlreadyConfirmedException(playerId)
        return Player(playerId, gameId, displayName, ConfirmedLocation(coordinate, at))
    }

    companion object {
        fun create(
            playerId: UUID,
            gameId: UUID,
            displayName: String?,
        ): Player = Player(playerId, gameId, DisplayName.resolve(displayName, playerId), null)

        fun restore(
            playerId: UUID,
            gameId: UUID,
            displayName: String,
            confirmedLocation: ConfirmedLocation?,
        ): Player = Player(playerId, gameId, displayName, confirmedLocation)
    }
}
