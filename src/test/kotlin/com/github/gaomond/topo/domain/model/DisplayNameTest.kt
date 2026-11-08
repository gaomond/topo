package com.github.gaomond.topo.domain.model

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

/** 未指定・空白の表示名に共通のフォールバックが適用されることを検証する。 */
class DisplayNameTest {
    private val playerId = UUID.fromString("abcdef01-2345-6789-abcd-ef0123456789")

    @Test
    fun test_resolveDisplayName_withValue_trimsAndReturnsAsIs() {
        assertEquals("たろう", DisplayName.resolve("  たろう  ", playerId))
    }

    @Test
    fun test_resolveDisplayName_withNull_returnsUuidPrefix8() {
        assertEquals(playerId.toString().take(8), DisplayName.resolve(null, playerId))
    }

    @Test
    fun test_resolveDisplayName_withBlank_returnsUuidPrefix8() {
        for (blank in listOf("", "   ", "\t")) {
            assertEquals(
                playerId.toString().take(8),
                DisplayName.resolve(blank, playerId),
                "blank=[$blank]",
            )
        }
    }
}
