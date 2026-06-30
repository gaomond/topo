package com.github.gaomond.topo.domain.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GameStatusTest {
    @Test
    fun test_values_always_returnsWaitingActiveCompletedInOrder() {
        assertEquals(
            listOf("WAITING", "ACTIVE", "COMPLETED"),
            GameStatus.entries.map { it.name },
        )
    }
}
