package com.github.gaomond.topo.domain.model

/**
 * 座標（緯度経度）の値オブジェクト（ユビキタス言語「座標 = Coordinate（lat / lng）」・US-07）。
 *
 * 「地球上に存在し得る座標」だけがインスタンス化できるよう、意味論的バリデーション（範囲・NaN 拒否）を
 * 型の不変条件に閉じ込める（spec 1.2 / D4）。範囲外・NaN・無限は [IllegalArgumentException] で弾き、
 * これを inbound で 400（Bad Request）に写像する。
 *
 * WGS84 / SRID 4326 を採用するが、Crs 種別はこれに限定しない（ユビキタス言語）。
 * Spring / JPA / PostGIS を一切 import しない純 Kotlin。
 *
 * @property lat 緯度（[-90.0, 90.0]）
 * @property lng 経度（[-180.0, 180.0]）
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
