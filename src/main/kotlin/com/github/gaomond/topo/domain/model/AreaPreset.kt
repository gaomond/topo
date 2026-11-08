package com.github.gaomond.topo.domain.model

/** 面積プリセット。sqm は面積閾値の実値（m²）。 */
data class AreaPreset(
    val key: String,
    val label: String,
    val sqm: Long,
) {
    companion object {
        /** small → medium → large の表示順を保証する。 */
        val ALL: List<AreaPreset> =
            listOf(
                AreaPreset(key = "small", label = "お手軽", sqm = 500_000L),
                AreaPreset(key = "medium", label = "ふつう", sqm = 2_000_000L),
                AreaPreset(key = "large", label = "がっつり", sqm = 10_000_000L),
            )

        /** 未知の key は null を返す。選択肢は [ALL] に限定する。 */
        fun byKey(key: String): AreaPreset? = ALL.firstOrNull { it.key == key }
    }
}
