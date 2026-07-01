package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.GameValidationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * ドメイン例外を HTTP ステータスにマッピングする inbound アダプタ。
 *
 * CLAUDE.md「Domain でドメイン例外を定義し、inbound で HTTP ステータスにマッピング
 * （バリデーション失敗 = 400）」に従う。US-04 が最初のエラーマッピング導入点。
 */
@RestControllerAdvice
class GameApiExceptionHandler {
    @ExceptionHandler(GameValidationException::class)
    fun handleValidation(ex: GameValidationException): ProblemDetail {
        val detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "不正なリクエストです")
        detail.setProperty("reason", ex.reason.name)
        return detail
    }
}
