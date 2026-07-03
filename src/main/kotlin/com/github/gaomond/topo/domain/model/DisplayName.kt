package com.github.gaomond.topo.domain.model

import java.util.UUID

/**
 * 表示名（displayName）のフォールバック規則（D6 案A）。
 *
 * 作成（US-04）と参加（US-05）で同一ルールを使うため、Domain の純関数として 1 箇所に集約する（DRY）。
 * Spring / JPA 非依存。
 */
object DisplayName {
    /** フォールバック時に使う playerId 先頭文字数。 */
    const val FALLBACK_LENGTH = 8

    /**
     * 表示名を確定する。null / 空 / 空白のみは playerId 先頭 [FALLBACK_LENGTH] 文字にフォールバックする。
     * 値がある場合は trim して返す（DB には常に非 null が入る）。
     *
     * @param raw      入力表示名（未送信は null）
     * @param playerId フォールバックの元にする player ID
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
