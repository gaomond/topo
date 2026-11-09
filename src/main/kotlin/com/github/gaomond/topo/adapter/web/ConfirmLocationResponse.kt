package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.ConfirmedLocationResult
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(requiredProperties = ["playerId", "confirmed", "confirmedAt", "result"])
data class ConfirmLocationResponse(
    @field:Schema(format = "uuid") val playerId: UUID,
    val confirmed: Boolean,
    @field:Schema(format = "date-time") val confirmedAt: Instant,
    val result: GameResultPayload? = null,
) {
    companion object {
        fun from(result: ConfirmedLocationResult): ConfirmLocationResponse =
            ConfirmLocationResponse(result.playerId, result.confirmed, result.confirmedAt, result.result?.let(GameResultPayload::from))
    }
}
