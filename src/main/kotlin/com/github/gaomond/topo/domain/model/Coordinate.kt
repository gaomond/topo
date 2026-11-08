package com.github.gaomond.topo.domain.model

/**
 * WGS84 の緯度経度の座標。範囲外・NaN・無限は [IllegalArgumentException] で拒否する。
 *
 * @property lat 緯度（-90.0 以上 90.0 以下）
 * @property lng 経度（-180.0 以上 180.0 以下）
 */
data class Coordinate(
    val lat: Double,
    val lng: Double,
) {
    init {
        // NaN は範囲比較（in）で常に false になり「範囲外」に紛れるため独立に検査する。
        require(!lat.isNaN()) { "lat が数値ではありません（NaN）" }
        require(!lng.isNaN()) { "lng が数値ではありません（NaN）" }
        // 無限は範囲チェックで false になり弾かれる（境界値 ±90 / ±180 は成立）。
        require(lat in -90.0..90.0) { "lat が範囲外です（-90..90）: $lat" }
        require(lng in -180.0..180.0) { "lng が範囲外です（-180..180）: $lng" }
    }
}
