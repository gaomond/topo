package com.github.gaomond.topo.adapter.web

import io.swagger.v3.oas.annotations.media.Schema

/** Spring Boot の形式不正応答を文書化する型。実際の応答生成はフレームワークが行う。 */
@Schema(requiredProperties = ["timestamp", "status", "error", "path"])
data class RequestErrorResponse(
    @field:Schema(format = "date-time")
    val timestamp: String,
    val status: Int,
    val error: String,
    val path: String,
)

/** 既存の ProblemDetail の公開項目。例外ハンドラの戻り値を置き換える型ではない。 */
@Schema(requiredProperties = ["detail", "instance", "status", "title"])
data class ApiProblemResponse(
    val detail: String,
    @field:Schema(format = "uri-reference")
    val instance: String,
    val status: Int,
    val title: String,
)

/** 業務上の入力不正でのみ reason を公開する。 */
@Schema(requiredProperties = ["detail", "instance", "status", "title", "reason"])
data class ValidationProblemResponse(
    val detail: String,
    @field:Schema(format = "uri-reference")
    val instance: String,
    val status: Int,
    val title: String,
    @field:Schema(description = "入力不正の理由コード", example = "INVALID_OBJECT_TYPE")
    val reason: String,
)
