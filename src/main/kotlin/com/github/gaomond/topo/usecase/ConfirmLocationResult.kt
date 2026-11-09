package com.github.gaomond.topo.usecase

import java.time.Instant
import java.util.UUID

data class ConfirmLocationResult(
    val playerId: UUID,
    val confirmed: Boolean,
    val confirmedAt: Instant,
)
