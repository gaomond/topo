package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.exception.InvalidCoordinateException
import com.github.gaomond.topo.domain.exception.PlayerNotFoundException
import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.usecase.UpdateLiveLocationInput
import com.github.gaomond.topo.usecase.UpdateLiveLocationUseCase
import jakarta.servlet.ServletException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.put
import java.util.UUID
import kotlin.test.assertSame

/** 更新成功時の空ボディ（204）、不在・非所属の 404、不正入力の 400 を検証する。 */
@WebMvcTest(controllers = [UpdateLocationController::class])
@Import(GameApiExceptionHandler::class)
class UpdateLocationControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var updateLiveLocationUseCase: UpdateLiveLocationUseCase

    private val gameId = UUID.randomUUID()
    private val playerId = UUID.randomUUID()

    private fun path() = "/api/games/$gameId/players/$playerId/location"

    @Test
    fun test_updateLocation_withValidCoordinate_returns204AndNoBody() {
        mockMvc
            .put(path()) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lat":35.68,"lng":139.76}"""
            }.andExpect {
                status { isNoContent() }
                content { string("") }
            }
        verify(updateLiveLocationUseCase).update(UpdateLiveLocationInput(gameId, playerId, Coordinate(35.68, 139.76)))
    }

    @Test
    fun test_updateLocation_whenPlayerNotFound_returns404() {
        whenever(updateLiveLocationUseCase.update(any()))
            .thenThrow(PlayerNotFoundException(gameId, playerId))
        mockMvc
            .put(path()) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lat":35.0,"lng":139.0}"""
            }.andExpect { status { isNotFound() } }
    }

    @Test
    fun test_updateLocation_whenLatOutOfRange_returns400() {
        mockMvc
            .put(path()) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lat":100.0,"lng":139.0}"""
            }.andExpect {
                status { isBadRequest() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.reason") { value("INVALID_COORDINATE") }
                jsonPath("$.detail") { value("lat が範囲外です（-90..90）: 100.0") }
            }
        verify(updateLiveLocationUseCase, never()).update(any())
    }

    @Test
    fun test_updateLocation_whenLngOutOfRange_returns400() {
        mockMvc
            .put(path()) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lat":35.0,"lng":200.0}"""
            }.andExpect {
                status { isBadRequest() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.reason") { value("INVALID_COORDINATE") }
                jsonPath("$.detail") { value("lng が範囲外です（-180..180）: 200.0") }
            }
        verify(updateLiveLocationUseCase, never()).update(any())
    }

    @Test
    fun test_updateLocation_withNaN_returns400() {
        // JSON の NaN トークンは Jackson 既定で非数値として拒否され、パース失敗で 400 になる。
        mockMvc
            .put(path()) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lat":NaN,"lng":139.0}"""
            }.andExpect { status { isBadRequest() } }
        verify(updateLiveLocationUseCase, never()).update(any())
    }

    @Test
    fun test_updateLocation_withMissingField_returns400() {
        // 非 null Double の欠損は Jackson/Kotlin モジュールがデシリアライズに失敗し 400 になる。
        mockMvc
            .put(path()) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lat":35.0}"""
            }.andExpect { status { isBadRequest() } }
        verify(updateLiveLocationUseCase, never()).update(any())
    }

    @Test
    fun test_updateLocation_atCoordinateBoundary_returns204() {
        mockMvc
            .put(path()) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lat":-90.0,"lng":180.0}"""
            }.andExpect {
                status { isNoContent() }
                content { string("") }
            }
        verify(updateLiveLocationUseCase).update(UpdateLiveLocationInput(gameId, playerId, Coordinate(-90.0, 180.0)))
    }

    @Test
    fun test_updateLocation_whenUseCaseThrowsCoordinateException_propagatesSameException() {
        val failure = InvalidCoordinateException("復元した座標が不正です")
        whenever(updateLiveLocationUseCase.update(any())).thenThrow(failure)

        val ex =
            assertThrows<ServletException> {
                mockMvc.put(path()) {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"lat":35.0,"lng":139.0}"""
                }
            }
        assertSame(failure, ex.cause)
    }

    @Test
    fun test_updateLocation_withNullCoordinate_returns400WithoutCallingUseCase() {
        mockMvc
            .put(path()) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lat":null,"lng":139.0}"""
            }.andExpect { status { isBadRequest() } }
        verify(updateLiveLocationUseCase, never()).update(any())
    }

    @Test
    fun test_updateLocation_withMalformedJson_returns400WithoutCallingUseCase() {
        mockMvc
            .put(path()) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lat":35.0,"lng":}"""
            }.andExpect { status { isBadRequest() } }
        verify(updateLiveLocationUseCase, never()).update(any())
    }
}
