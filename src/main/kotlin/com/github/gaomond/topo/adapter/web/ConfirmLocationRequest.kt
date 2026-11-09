package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.usecase.ConfirmLocationInput
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

/**
 * ゲーム ID とプレイヤー ID はパスに指定し、ボディには座標を指定する。
 * 座標は必須。欠損・デシリアライズ不能な値は 400、範囲外の値も 400 となる。
 *
 * @param lat 緯度（-90 以上 90 以下、WGS84 / SRID 4326）。
 * @param lng 経度（-180 以上 180 以下、WGS84 / SRID 4326）。
 */
data class ConfirmLocationRequest(
    @field:Schema(minimum = "-90", maximum = "90", description = "WGS84の緯度", requiredMode = Schema.RequiredMode.REQUIRED)
    val lat: Double,
    @field:Schema(minimum = "-180", maximum = "180", description = "WGS84の経度", requiredMode = Schema.RequiredMode.REQUIRED)
    val lng: Double,
) {
    fun toInput(
        gameId: UUID,
        playerId: UUID,
    ): ConfirmLocationInput = ConfirmLocationInput(gameId, playerId, Coordinate(lat, lng))
}
