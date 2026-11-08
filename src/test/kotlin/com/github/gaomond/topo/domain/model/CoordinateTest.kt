package com.github.gaomond.topo.domain.model

import com.github.gaomond.topo.domain.exception.InvalidCoordinateException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

/** 座標の範囲境界・NaN・無限の拒否を検証する。 */
class CoordinateTest {
    @Test
    fun test_construct_withValidValue_holdsLatLng() {
        val coordinate = Coordinate(lat = 35.68, lng = 139.76)
        assertEquals(35.68, coordinate.lat)
        assertEquals(139.76, coordinate.lng)
    }

    @Test
    fun test_construct_atBoundaryValues_succeeds() {
        // 境界値（±90 / ±180）は成立する（以下/以上を含む閉区間）。
        Coordinate(lat = 90.0, lng = 180.0)
        Coordinate(lat = -90.0, lng = -180.0)
        Coordinate(lat = 0.0, lng = 0.0)
    }

    @Test
    fun test_construct_withLatOutOfRange_throws() {
        for (lat in listOf(90.0001, -90.0001)) {
            val ex = assertThrows<InvalidCoordinateException> { Coordinate(lat = lat, lng = 0.0) }
            assertEquals("lat が範囲外です（-90..90）: $lat", ex.message)
        }
    }

    @Test
    fun test_construct_withLngOutOfRange_throws() {
        for (lng in listOf(180.0001, -180.0001)) {
            val ex = assertThrows<InvalidCoordinateException> { Coordinate(lat = 0.0, lng = lng) }
            assertEquals("lng が範囲外です（-180..180）: $lng", ex.message)
        }
    }

    @Test
    fun test_construct_withNaN_throws() {
        val lat = assertThrows<InvalidCoordinateException> { Coordinate(lat = Double.NaN, lng = 0.0) }
        val lng = assertThrows<InvalidCoordinateException> { Coordinate(lat = 0.0, lng = Double.NaN) }
        assertEquals("lat が数値ではありません（NaN）", lat.message)
        assertEquals("lng が数値ではありません（NaN）", lng.message)
    }

    @Test
    fun test_construct_withInfinity_throws() {
        for (value in listOf(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val lat = assertThrows<InvalidCoordinateException> { Coordinate(lat = value, lng = 0.0) }
            val lng = assertThrows<InvalidCoordinateException> { Coordinate(lat = 0.0, lng = value) }
            assertEquals("lat が範囲外です（-90..90）: $value", lat.message)
            assertEquals("lng が範囲外です（-180..180）: $value", lng.message)
        }
    }

    @Test
    fun test_construct_withMultipleViolations_preservesValidationOrder() {
        for ((lat, lng, message) in listOf(
            Triple(Double.NaN, Double.NaN, "lat が数値ではありません（NaN）"),
            Triple(100.0, Double.NaN, "lng が数値ではありません（NaN）"),
            Triple(100.0, 200.0, "lat が範囲外です（-90..90）: 100.0"),
        )) {
            assertEquals(message, assertThrows<InvalidCoordinateException> { Coordinate(lat, lng) }.message)
        }
    }

    @Test
    fun test_copy_withInvalidValue_rejectsInvalidCoordinate() {
        val coordinate = Coordinate(35.0, 139.0)
        assertThrows<InvalidCoordinateException> { coordinate.copy(lng = 200.0) }
    }
}
