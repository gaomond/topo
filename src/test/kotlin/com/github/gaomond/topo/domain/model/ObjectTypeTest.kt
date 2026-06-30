package com.github.gaomond.topo.domain.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ObjectTypeTest {
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
