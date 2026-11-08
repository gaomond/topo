package com.github.gaomond.topo.usecase

import java.util.UUID

/** Controller から受け取る操作の入力。 */
data class GetGameStateInput(
    val gameId: UUID,
)
