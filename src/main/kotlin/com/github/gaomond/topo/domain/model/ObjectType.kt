package com.github.gaomond.topo.domain.model

/** オブジェクト種別。jsonValue は enum 名とは異なる外部表現（スネークケース）。 */
enum class ObjectType(
    val jsonValue: String,
) {
    SHRINE("shrine"),
    TEMPLE("temple"),
    SCHOOL("school"),
    CONVENIENCE_STORE("convenience_store"),
    PARK("park"),
    STATION("station"),
    ;

    companion object {
        /** 集計対象として選択可能な種別。表示順を持つ。 */
        val SELECTABLE: List<ObjectType> = listOf(SHRINE)

        /** 選択肢 [SELECTABLE] にない値は、enum に存在する種別でも null を返す。 */
        fun selectableFromJsonValueOrNull(jsonValue: String): ObjectType? = SELECTABLE.firstOrNull { it.jsonValue == jsonValue }
    }
}
