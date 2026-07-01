package com.github.gaomond.topo.domain.model

/**
 * 面積プリセット（areaPreset）。
 *
 * @param key   選択肢キー（small / medium / large）
 * @param label 表示名
 * @param sqm   面積閾値（areaThreshold）の実値（m²）
 */
data class AreaPreset(
    val key: String,
    val label: String,
    val sqm: Long,
) {
    companion object {
        /**
         * 面積プリセット一覧。small → medium → large の順を保証する。
         */
        val ALL: List<AreaPreset> =
            listOf(
                AreaPreset(key = "small", label = "お手軽", sqm = 500_000L),
                AreaPreset(key = "medium", label = "ふつう", sqm = 2_000_000L),
                AreaPreset(key = "large", label = "がっつり", sqm = 10_000_000L),
            )

        /**
         * プリセット key（small / medium / large）から対応する [AreaPreset] を引く。
         * 未知の key は null を返す（呼び出し側でバリデーション失敗として扱う）。
         * config が公開する [ALL] を単一ソースにする（DRY）。
         */
        fun byKey(key: String): AreaPreset? = ALL.firstOrNull { it.key == key }
    }
}
