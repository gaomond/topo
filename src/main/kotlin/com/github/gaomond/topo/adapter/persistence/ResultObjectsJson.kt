package com.github.gaomond.topo.adapter.persistence

import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.model.GameObjectSnapshot
import com.github.gaomond.topo.domain.model.ObjectType
import tools.jackson.databind.json.JsonMapper

internal object ResultObjectsJson {
    private val mapper = JsonMapper()

    fun write(objects: List<GameObjectSnapshot>): String =
        mapper.writeValueAsString(
            objects.map {
                mapOf(
                    "id" to it.id.toString(),
                    "objectType" to it.objectType.jsonValue,
                    "name" to it.name,
                    "lat" to it.coordinate.lat,
                    "lng" to it.coordinate.lng,
                )
            },
        )

    fun read(json: String): List<GameObjectSnapshot> {
        val array = mapper.readTree(json)
        check(array.isArray) { "獲得明細が配列ではありません" }
        return array
            .iterator()
            .asSequence()
            .map { node ->
                check(node.isObject && node.has("id") && node.has("objectType") && node.has("name") && node.has("lat") && node.has("lng")) {
                    "獲得明細の必須項目がありません"
                }
                val id = node.path("id")
                val type = node.path("objectType")
                val name = node.path("name")
                val lat = node.path("lat")
                val lng = node.path("lng")
                check(id.isString && type.isString && (name.isNull || name.isString) && lat.isNumber && lng.isNumber) {
                    "獲得明細の項目型が不正です"
                }
                val numericId = id.asString().toLongOrNull()
                check(numericId != null && numericId.toString() == id.asString()) { "獲得明細のIDが不正です" }
                GameObjectSnapshot(
                    numericId,
                    ObjectType.entries.single { it.jsonValue == type.asString() },
                    if (name.isNull) null else name.asString(),
                    Coordinate(lat.asDouble(), lng.asDouble()),
                )
            }.toList()
    }
}
