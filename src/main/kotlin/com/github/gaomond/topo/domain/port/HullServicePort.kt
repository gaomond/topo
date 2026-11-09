package com.github.gaomond.topo.domain.port

import com.github.gaomond.topo.domain.model.ConfirmationArea
import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.model.GameObjectSnapshot
import com.github.gaomond.topo.domain.model.ObjectType
import java.util.UUID

interface HullServicePort {
    /** 保存済み確定点に候補を1点加える。人数は重複を除かず、退化面積は0として返す。 */
    fun calculateArea(
        gameId: UUID,
        candidate: Coordinate,
    ): ConfirmationArea

    /** 計算済み凸包の境界を含む対象の獲得時点の情報を返す。凸包・面積を再計算しない。 */
    fun findCoveredObjects(
        objectType: ObjectType,
        hull: List<Coordinate>,
    ): List<GameObjectSnapshot>
}
