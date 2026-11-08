package com.github.gaomond.topo.domain.model

import java.util.UUID

/** 作成者と参加者に共通の表示名フォールバック規則。 */
object DisplayName {
    const val FALLBACK_LENGTH = 8

    /**
     * null・空・空白のみは playerId 先頭 [FALLBACK_LENGTH] 文字にフォールバックする。
     * 値がある場合は前後の空白を除去する。
     */
    fun resolve(
        raw: String?,
        playerId: UUID,
    ): String {
        val trimmed = raw?.trim()
        return if (trimmed.isNullOrEmpty()) {
            playerId.toString().take(FALLBACK_LENGTH)
        } else {
            trimmed
        }
    }
}
