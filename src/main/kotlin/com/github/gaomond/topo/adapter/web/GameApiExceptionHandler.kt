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
 * ドメイン例外を HTTP ステータスにマッピングする inbound アダプタ。
 *
 * CLAUDE.md「Domain でドメイン例外を定義し、inbound で HTTP ステータスにマッピング
 * （バリデーション失敗 = 400 / 対象不在 = 404）」に従う。
 * - [GameValidationException] → 400（US-04: 作成入力の検証 / US-07: 座標の意味論検証）
 * - [GameNotFoundException] → 404（US-05: 不在の gameId）
 * - [PlayerNotFoundException] → 404（US-07: player 不在 / 当該 game への非所属。理由は出し分けない）
 * - [GameJoinNotAllowedException] → 409（US-05: WAITING 以外 / 定員到達。理由は出し分けない）
 * - [NotGameCreatorException] → 403（US-06: 開始をリクエストしたのが作成者でない）
 * - [GameStartNotAllowedException] → 409（US-06: WAITING 以外 / 定員未達。理由は出し分けない）
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
        // 不在 / 非所属は区別しない（spec 1.1）。
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "プレイヤーが見つかりません")

    @ExceptionHandler(GameJoinNotAllowedException::class)
    fun handleJoinNotAllowed(ex: GameJoinNotAllowedException): ProblemDetail =
        // 仕様上 409 の理由は出し分けない（reason はレスポンスに含めない）。
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "このゲームには参加できません")

    @ExceptionHandler(NotGameCreatorException::class)
    fun handleNotCreator(ex: NotGameCreatorException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "ゲームの作成者のみ開始できます")

    @ExceptionHandler(GameStartNotAllowedException::class)
    fun handleStartNotAllowed(ex: GameStartNotAllowedException): ProblemDetail =
        // 仕様上 409 の理由は出し分けない（reason はレスポンスに含めない）。
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "このゲームは開始できません")
}
