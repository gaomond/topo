package com.github.gaomond.topo.usecase

import java.util.UUID

/** 参加入力。displayName が null・空・空白のみの場合はフォールバックする。 */
data class JoinGameInput(
    val gameId: UUID,
    val displayName: String? = null,
)

/** 新規参加者の ID。 */
data class JoinGameResult(
    val playerId: UUID,
)
