package com.github.gaomond.topo.adapter.persistence

import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.model.GameObjectSnapshot
import com.github.gaomond.topo.domain.model.ObjectType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class ResultObjectsJsonTest {
    @Test fun test_json_largeIdAndNullName_roundTripsEveryField() {
        val objects = listOf(GameObjectSnapshot(9007199254740993L, ObjectType.SHRINE, null, Coordinate(35.0, 139.0)))
        assertEquals(objects, ResultObjectsJson.read(ResultObjectsJson.write(objects)))
        assertEquals(emptyList(), ResultObjectsJson.read("[]"))
    }

    @Test fun test_read_invalidFieldTypesOrMissingName_rejects() {
        val valid = """{"id":"2","objectType":"shrine","name":null,"lat":35.0,"lng":139.0}"""
        for (invalid in listOf(
            "{}",
            "null",
            "[{}]",
            "[$valid]".replace("\"name\":null,", ""),
            "[$valid]".replace("\"id\":\"2\"", "\"id\":2"),
            "[$valid]".replace("35.0", "\"35\""),
            "[$valid]".replace("\"2\"", "\"9223372036854775808\""),
        )) {
            assertThrows<IllegalStateException> { ResultObjectsJson.read(invalid) }
        }
    }
}
