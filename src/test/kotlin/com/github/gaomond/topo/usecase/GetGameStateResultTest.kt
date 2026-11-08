package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.model.GameStatus
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertNull

/** 未計測の暫定面積を null で表現できることを検証する。 */
class GetGameStateResultTest {
    @Test
    fun test_getGameStateResult_withoutCurrentArea_defaultsToNull() {
        val state =
            GetGameStateResult(
                gameId = UUID.randomUUID(),
                status = GameStatus.WAITING,
                playerCount = 3,
                creatorPlayerId = null,
                players = emptyList(),
                areaThreshold = 2_000_000.0,
            )
        assertNull(state.currentArea)
    }
}
