package com.github.gaomond.topo.domain.model

/**
 * ゲームオブジェクトの種別（objectType）。
 *
 * DESIGN.md のユビキタス言語に対応する。`jsonValue` は API / DB で用いる
 * スネークケース文字列で、enum 名（例: SHRINE）とは区別する。
 */
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
        /**
         * 集計対象として選択可能な種別。MVP は shrine のみ。
         * 順序を持つ List として公開する（クライアントの表示順に使える）。
         */
        val SELECTABLE: List<ObjectType> = listOf(SHRINE)
    }
}
