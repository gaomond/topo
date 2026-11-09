package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.exception.InvalidCoordinateException
import com.github.gaomond.topo.usecase.ConfirmLocationUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@Tag(name = "プレイ中")
class ConfirmLocationController(
    private val confirmLocationUseCase: ConfirmLocationUseCase,
) {
    @Operation(
        operationId = "confirmLocation",
        summary = "現在地を確定する",
        description = "ACTIVEの未確定プレイヤーのみ確定できる。保存した確定点は変更できない。",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "位置を確定した",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ConfirmLocationResponse::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "IDやリクエストの形式が不正、または座標が不正",
                content = [
                    Content(mediaType = "application/json", schema = Schema(implementation = RequestErrorResponse::class)),
                    Content(mediaType = "application/problem+json", schema = Schema(implementation = ValidationProblemResponse::class)),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "ゲームまたはプレイヤーが見つからない、あるいは非所属",
                content = [
                    Content(mediaType = "application/problem+json", schema = Schema(implementation = ApiProblemResponse::class)),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "非ACTIVEまたは確定済み",
                content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ApiProblemResponse::class))],
            ),
        ],
    )
    @PostMapping("/api/games/{id}/players/{pid}/confirm")
    fun confirmLocation(
        @PathVariable id: UUID,
        @PathVariable pid: UUID,
        @RequestBody request: ConfirmLocationRequest,
    ): ResponseEntity<*> {
        // 入力からの生成だけを400にし、後段の同じ違反は内部異常として伝播させる。
        val input =
            try {
                request.toInput(id, pid)
            } catch (ex: InvalidCoordinateException) {
                val detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "座標が不正です")
                detail.setProperty("reason", "INVALID_COORDINATE")
                return ResponseEntity.badRequest().body(detail)
            }
        return ResponseEntity.ok(ConfirmLocationResponse.from(confirmLocationUseCase.confirm(input)))
    }
}
