package com.github.gaomond.topo.domain.model

import java.util.UUID

class Player private constructor(
    val playerId: UUID,
    val gameId: UUID,
    val displayName: String,
) {
    companion object {
        fun create(
            playerId: UUID,
            gameId: UUID,
            displayName: String?,
        ): Player = Player(playerId, gameId, DisplayName.resolve(displayName, playerId))
    }
}
