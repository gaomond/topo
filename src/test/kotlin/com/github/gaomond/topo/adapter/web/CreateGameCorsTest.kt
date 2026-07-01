package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.config.WebCorsConfig
import com.github.gaomond.topo.domain.model.GameCreationResult
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

/**
 * US-04 Level 3（CORS）検証。許可オリジン（application.properties: localhost:5173）からの
 * プリフライト（OPTIONS）が通り、未許可オリジンは拒否されることを確認する。
 * テスト対象は src/ の [WebCorsConfig] を import。
 */
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
            .thenReturn(GameCreationResult(UUID.randomUUID(), UUID.randomUUID()))
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
