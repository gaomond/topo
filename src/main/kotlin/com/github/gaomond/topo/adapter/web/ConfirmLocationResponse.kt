package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.ConfirmLocationResult
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(requiredProperties = ["playerId", "confirmed", "confirmedAt"])
data class ConfirmLocationResponse(
    @field:Schema(format = "uuid") val playerId: UUID,
    val confirmed: Boolean,
    @field:Schema(format = "date-time") val confirmedAt: Instant,
) {
    companion object {
        fun from(result: ConfirmLocationResult): ConfirmLocationResponse =
            ConfirmLocationResponse(result.playerId, result.confirmed, result.confirmedAt)
    }
}
