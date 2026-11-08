package com.github.gaomond.topo.usecase

import java.util.UUID

data class StartGameInput(
    val gameId: UUID,
    val playerId: UUID,
)
