package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.exception.GameJoinNotAllowedException
import com.github.gaomond.topo.domain.exception.GameNotFoundException
import com.github.gaomond.topo.domain.exception.GameStartNotAllowedException
import com.github.gaomond.topo.domain.exception.GameValidationException
import com.github.gaomond.topo.domain.exception.NotGameCreatorException
import com.github.gaomond.topo.domain.exception.PlayerNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 入力不正は 400、対象不在は 404、作成者以外の開始は 403、参加・開始不可は 409 とする。
 *
 * 入力不正のみ reason を公開する。プレイヤーの不在・非所属、参加・開始不可の個別理由は
 * レスポンスで出し分けない。
 */
@RestControllerAdvice
class GameApiExceptionHandler {
    @ExceptionHandler(GameValidationException::class)
    fun handleValidation(ex: GameValidationException): ProblemDetail {
        val detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "不正なリクエストです")
        detail.setProperty("reason", ex.reason.name)
        return detail
    }

    @ExceptionHandler(GameNotFoundException::class)
    fun handleNotFound(ex: GameNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "ゲームが見つかりません")

    @ExceptionHandler(PlayerNotFoundException::class)
    fun handlePlayerNotFound(ex: PlayerNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "プレイヤーが見つかりません")

    @ExceptionHandler(GameJoinNotAllowedException::class)
    fun handleJoinNotAllowed(ex: GameJoinNotAllowedException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "このゲームには参加できません")

    @ExceptionHandler(NotGameCreatorException::class)
    fun handleNotCreator(ex: NotGameCreatorException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "ゲームの作成者のみ開始できます")

    @ExceptionHandler(GameStartNotAllowedException::class)
    fun handleStartNotAllowed(ex: GameStartNotAllowedException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "このゲームは開始できません")
}
