package com.github.gaomond.topo.usecase

import java.util.UUID

/** Controller から受け取る操作の入力。 */
data class StartGameInput(
    val gameId: UUID,
    val playerId: UUID,
)
