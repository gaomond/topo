package com.github.gaomond.topo.domain.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AreaPresetTest {
    @Test
    fun test_all_bySpec_holdsThreePresetsInSmallToLargeOrder() {
        val keys = AreaPreset.ALL.map { it.key }
        assertEquals(listOf("small", "medium", "large"), keys)
    }

    @Test
    fun test_all_eachPreset_hasLabelAndSqmAsSpecified() {
        val byKey = AreaPreset.ALL.associateBy { it.key }

        assertEquals("お手軽", byKey.getValue("small").label)
        assertEquals(500_000L, byKey.getValue("small").sqm)

        assertEquals("ふつう", byKey.getValue("medium").label)
        assertEquals(2_000_000L, byKey.getValue("medium").sqm)

        assertEquals("がっつり", byKey.getValue("large").label)
        assertEquals(10_000_000L, byKey.getValue("large").sqm)
    }
}
