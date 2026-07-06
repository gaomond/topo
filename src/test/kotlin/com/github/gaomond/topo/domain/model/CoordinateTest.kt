package com.github.gaomond.topo.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

/**
 * US-07 Level 1（Domain）検証。[Coordinate] の不変条件（範囲・NaN・無限）を検証する。
 * テスト対象は src/ の [Coordinate] を import（再定義しない）。
 */
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
        assertThrows<IllegalArgumentException> { Coordinate(lat = 90.0001, lng = 0.0) }
        assertThrows<IllegalArgumentException> { Coordinate(lat = -90.0001, lng = 0.0) }
    }

    @Test
    fun test_construct_withLngOutOfRange_throws() {
        assertThrows<IllegalArgumentException> { Coordinate(lat = 0.0, lng = 180.0001) }
        assertThrows<IllegalArgumentException> { Coordinate(lat = 0.0, lng = -180.0001) }
    }

    @Test
    fun test_construct_withNaN_throws() {
        assertThrows<IllegalArgumentException> { Coordinate(lat = Double.NaN, lng = 0.0) }
        assertThrows<IllegalArgumentException> { Coordinate(lat = 0.0, lng = Double.NaN) }
    }

    @Test
    fun test_construct_withInfinity_throws() {
        assertThrows<IllegalArgumentException> { Coordinate(lat = Double.POSITIVE_INFINITY, lng = 0.0) }
        assertThrows<IllegalArgumentException> { Coordinate(lat = 0.0, lng = Double.NEGATIVE_INFINITY) }
    }
}
