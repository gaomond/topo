package com.github.gaomond.topo.adapter.web

/**
 * PUT /api/games/{id}/players/{pid}/location のリクエストボディ（Web 表現・US-07）。
 *
 * gameId / pid はパス変数から渡す（ボディには含めない）。lat / lng は必須。
 * 非 null Double のため欠損時は Jackson/Kotlin モジュールがデシリアライズに失敗し、
 * Spring 既定の HttpMessageNotReadableException → 400 になる（座標の意味論検証は Domain の Coordinate が担う）。
 *
 * @param lat 緯度（WGS84 / SRID 4326）
 * @param lng 経度（WGS84 / SRID 4326）
 */
data class UpdateLocationRequest(
    val lat: Double,
    val lng: Double,
)
