package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.config.WebCorsConfig
import com.github.gaomond.topo.usecase.CreateGameResult
import com.github.gaomond.topo.usecase.CreateGameUseCase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.options
import org.springframework.test.web.servlet.post
import java.util.UUID

/** 許可オリジンからのプリフライトを受け付け、未許可オリジンを拒否することを検証する。 */
@WebMvcTest(controllers = [CreateGameController::class])
@Import(WebCorsConfig::class)
class CreateGameCorsTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var createGameUseCase: CreateGameUseCase

    private val allowedOrigin = "http://localhost:5173"
    private val disallowedOrigin = "http://evil.example.com"

    @Test
    fun test_optionsGames_fromAllowedOrigin_returns200WithCorsHeaders() {
        mockMvc
            .options("/api/games") {
                header(HttpHeaders.ORIGIN, allowedOrigin)
                header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
            }.andExpect {
                status { isOk() }
                header { string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowedOrigin) }
            }
    }

    @Test
    fun test_postGames_fromAllowedOrigin_hasCorsAllowOriginHeader() {
        whenever(createGameUseCase.create(any()))
            .thenReturn(CreateGameResult(UUID.randomUUID(), UUID.randomUUID()))
        mockMvc
            .post("/api/games") {
                header(HttpHeaders.ORIGIN, allowedOrigin)
                contentType = MediaType.APPLICATION_JSON
                content = """{"objectType":"shrine","areaPreset":"medium","playerCount":3}"""
            }.andExpect {
                status { isCreated() }
                header { string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowedOrigin) }
            }
    }

    @Test
    fun test_optionsGames_fromDisallowedOrigin_isForbidden() {
        mockMvc
            .options("/api/games") {
                header(HttpHeaders.ORIGIN, disallowedOrigin)
                header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
            }.andExpect {
                status { isForbidden() }
            }
    }
}
