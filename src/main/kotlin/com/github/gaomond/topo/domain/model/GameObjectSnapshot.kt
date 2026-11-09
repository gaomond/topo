package com.github.gaomond.topo.domain.model

data class GameObjectSnapshot(
    val id: Long,
    val objectType: ObjectType,
    val name: String?,
    val coordinate: Coordinate,
)
