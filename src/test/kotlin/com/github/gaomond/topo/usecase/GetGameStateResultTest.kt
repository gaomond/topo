package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.model.GameStatus
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertNull

/** 状態取得結果の既存の既定値を移動後も維持する。 */
class GetGameStateResultTest {
    @Test
    fun test_getGameStateResult_withoutCurrentArea_defaultsToNull() {
        // 3 点未満などで currentArea が無い状態を null で表現できる（既定 null）。
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
