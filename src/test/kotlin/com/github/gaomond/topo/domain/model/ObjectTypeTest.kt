package com.github.gaomond.topo.domain.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ObjectTypeTest {
    @Test
    fun test_selectableFromJsonValueOrNull_withShrine_returnsShrine() {
        assertEquals(ObjectType.SHRINE, ObjectType.selectableFromJsonValueOrNull("shrine"))
    }

    @Test
    fun test_selectableFromJsonValueOrNull_withNonSelectableEnumValue_returnsNull() {
        // enum には存在するが SELECTABLE 外（temple / school 等）は許可しない（検証範囲＝SELECTABLE のみ）。
        assertNull(ObjectType.selectableFromJsonValueOrNull("temple"))
        assertNull(ObjectType.selectableFromJsonValueOrNull("school"))
        assertNull(ObjectType.selectableFromJsonValueOrNull("station"))
    }

    @Test
    fun test_selectableFromJsonValueOrNull_withUnknownValue_returnsNull() {
        assertNull(ObjectType.selectableFromJsonValueOrNull("mountain"))
        assertNull(ObjectType.selectableFromJsonValueOrNull(""))
    }

    @Test
    fun test_selectable_forMvp_holdsJsonValueListOfShrineOnly() {
        assertEquals(listOf("shrine"), ObjectType.SELECTABLE.map { it.jsonValue })
    }

    @Test
    fun test_jsonValue_forEachType_matchesSnakeCaseStringInDesign() {
        assertEquals("shrine", ObjectType.SHRINE.jsonValue)
        assertEquals("temple", ObjectType.TEMPLE.jsonValue)
        assertEquals("school", ObjectType.SCHOOL.jsonValue)
        assertEquals("convenience_store", ObjectType.CONVENIENCE_STORE.jsonValue)
        assertEquals("park", ObjectType.PARK.jsonValue)
        assertEquals("station", ObjectType.STATION.jsonValue)
    }
}
