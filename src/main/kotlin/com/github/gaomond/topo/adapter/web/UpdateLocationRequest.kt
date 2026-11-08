package com.github.gaomond.topo.adapter.web

/**
 * ゲーム ID とプレイヤー ID はパスに指定し、ボディには座標を指定する。
 * 座標は必須。欠損・デシリアライズ不能な値は 400、範囲外の値も 400 となる。
 *
 * @param lat 緯度（-90 以上 90 以下、WGS84 / SRID 4326）。
 * @param lng 経度（-180 以上 180 以下、WGS84 / SRID 4326）。
 */
data class UpdateLocationRequest(
    val lat: Double,
    val lng: Double,
)
