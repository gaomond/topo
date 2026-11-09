package com.github.gaomond.topo.domain.model

import com.github.gaomond.topo.domain.exception.ConfirmationAreaRejectedException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GameAggregationTest {
    private fun game(count: Int = 3) = Game.restore(UUID.randomUUID(), GameStatus.ACTIVE, count, null, ObjectType.SHRINE, 100.0)

    private val polygon = listOf(Coordinate(35.0, 139.0), Coordinate(35.0, 139.001), Coordinate(35.001, 139.0), Coordinate(35.0, 139.0))

    @Test fun test_validateArea_areaBoundaries_acceptsInclusiveLimits() {
        for (sqm in listOf(1.0, 100.0)) {
            val candidate = ConfirmationArea(3, sqm, polygon)
            val accepted = game().validateArea(candidate)
            assertTrue(accepted.isFinal)
            assertSame(candidate, accepted.area)
        }
    }

    @Test fun test_validateArea_areaOutsideLimits_rejectsWithReason() {
        assertEquals(
            ConfirmationAreaRejectedException.Reason.AREA_BELOW_MIN,
            assertThrows<ConfirmationAreaRejectedException> {
                game().validateArea(ConfirmationArea(3, 0.999, polygon))
            }.reason,
        )
        assertEquals(
            ConfirmationAreaRejectedException.Reason.AREA_EXCEEDS_THRESHOLD,
            assertThrows<ConfirmationAreaRejectedException> {
                game(4).validateArea(ConfirmationArea(3, 100.001, polygon))
            }.reason,
        )
    }

    @Test fun test_validateArea_nonFinalZero_acceptsWithoutLowerLimit() {
        assertFalse(game().validateArea(ConfirmationArea(2, 0.0, listOf(polygon[0]))).isFinal)
    }

    @Test fun test_complete_acceptedFinalCandidate_preservesCalculatedResult() {
        val game = game(4)
        val candidate = ConfirmationArea(4, 50.0, polygon)
        val completed = game.complete(game.validateArea(candidate), emptyList())
        assertEquals(GameStatus.COMPLETED, completed.status)
        assertEquals(GameStatus.ACTIVE, game.status)
        assertEquals(GameResult(50.0, true, polygon, emptyList()), completed.result)
    }

    @Test fun test_complete_objectsSnapshot_keepsDistinctIdsAndNumericOrder() {
        val game = game()
        val objects = listOf(10L, 2L).map { GameObjectSnapshot(it, ObjectType.SHRINE, null, polygon[0]) }
        val result = game.complete(game.validateArea(ConfirmationArea(3, 50.0, polygon)), objects).result!!
        assertEquals(listOf(2L, 10L), result.objects.map { it.id })
        assertEquals(2, result.objectCount)
    }

    @Test fun test_complete_duplicateIdsOrWrongType_rejectsResult() {
        val game = game()
        val area = game.validateArea(ConfirmationArea(3, 50.0, polygon))
        val obj = GameObjectSnapshot(1L, ObjectType.SHRINE, "神社", polygon[0])
        assertThrows<IllegalStateException> { game.complete(area, listOf(obj, obj)) }
        assertThrows<IllegalStateException> { game.complete(area, listOf(obj.copy(objectType = ObjectType.TEMPLE))) }
    }

    @Test fun test_complete_mutableInputsAndExposedLists_preservesSnapshot() {
        val game = game()
        val points = polygon.toMutableList()
        val objects = mutableListOf(GameObjectSnapshot(9007199254740993L, ObjectType.SHRINE, null, polygon[0]))
        val result = game.complete(game.validateArea(ConfirmationArea(3, 50.0, points)), objects).result!!
        objects.clear()
        points.clear()
        assertEquals(1, result.objectCount)
        assertEquals(4, result.polygon.size)
        assertThrows<UnsupportedOperationException> { (result.objects as MutableList).clear() }
        assertThrows<UnsupportedOperationException> { (result.polygon as MutableList).clear() }
    }
}
