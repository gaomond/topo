package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.exception.InvalidCoordinateException
import com.github.gaomond.topo.usecase.UpdateLiveLocationUseCase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestComponent
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** MockMvcでは通らない、サーブレットコンテナのエラーディスパッチ後のHTTP応答を検証する。 */
@SpringBootTest(
    classes = [UpdateLocationHttpTest.TestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
class UpdateLocationHttpTest {
    @TestComponent
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = [DataSourceAutoConfiguration::class])
    @Import(UpdateLocationController::class, GameApiExceptionHandler::class)
    class TestApplication

    @LocalServerPort
    private var port: Int = 0

    @MockitoBean
    lateinit var updateLiveLocationUseCase: UpdateLiveLocationUseCase

    @Test
    fun test_updateLocation_withInvalidRequestCoordinate_returns400Problem() {
        val response = updateLocation("""{"lat":100.0,"lng":139.0}""")

        assertEquals(400, response.statusCode())
        assertTrue(
            response
                .headers()
                .firstValue("content-type")
                .orElse("")
                .startsWith("application/problem+json"),
        )
        val body = JsonMapper().readTree(response.body())
        assertEquals(400, body.path("status").asInt())
        assertEquals("INVALID_COORDINATE", body.path("reason").asString())
        assertEquals("lat が範囲外です（-90..90）: 100.0", body.path("detail").asString())
        assertTrue(body.path("instance").asString().endsWith("/location"))
        verify(updateLiveLocationUseCase, never()).update(any())
    }

    @Test
    fun test_updateLocation_whenUseCaseThrowsSameCoordinateException_returns500() {
        whenever(updateLiveLocationUseCase.update(any()))
            .thenThrow(InvalidCoordinateException("復元した座標が不正です"))

        val response = updateLocation("""{"lat":35.0,"lng":139.0}""")

        assertEquals(500, response.statusCode())
        val body = JsonMapper().readTree(response.body())
        assertEquals(500, body.path("status").asInt())
        assertFalse(body.has("reason"))
        assertFalse(response.body().contains("復元した座標が不正です"))
        verify(updateLiveLocationUseCase).update(any())
    }

    private fun updateLocation(body: String): HttpResponse<String> {
        val path = "/api/games/${UUID.randomUUID()}/players/${UUID.randomUUID()}/location"
        val request =
            HttpRequest
                .newBuilder(URI("http://localhost:$port$path"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build()
        return HttpClient.newHttpClient().use { it.send(request, HttpResponse.BodyHandlers.ofString()) }
    }
}
