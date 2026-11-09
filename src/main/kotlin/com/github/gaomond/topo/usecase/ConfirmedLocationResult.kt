package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.model.GameResult
import java.time.Instant
import java.util.UUID

data class ConfirmedLocationResult(
    val playerId: UUID,
    val confirmed: Boolean,
    val confirmedAt: Instant,
    val result: GameResult? = null,
)
