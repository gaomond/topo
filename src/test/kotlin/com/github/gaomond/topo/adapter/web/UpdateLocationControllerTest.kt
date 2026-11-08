package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.exception.GameValidationException
import com.github.gaomond.topo.domain.exception.PlayerNotFoundException
import com.github.gaomond.topo.usecase.UpdateLiveLocationInput
import com.github.gaomond.topo.usecase.UpdateLiveLocationUseCase
import org.junit.jupiter.api.Test
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
        verify(updateLiveLocationUseCase).update(UpdateLiveLocationInput(gameId, playerId, 35.68, 139.76))
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
        whenever(updateLiveLocationUseCase.update(any()))
            .thenThrow(
                GameValidationException(GameValidationException.Reason.INVALID_COORDINATE, "lat が範囲外"),
            )
        mockMvc
            .put(path()) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lat":100.0,"lng":139.0}"""
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun test_updateLocation_whenLngOutOfRange_returns400() {
        whenever(updateLiveLocationUseCase.update(any()))
            .thenThrow(
                GameValidationException(GameValidationException.Reason.INVALID_COORDINATE, "lng が範囲外"),
            )
        mockMvc
            .put(path()) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lat":35.0,"lng":200.0}"""
            }.andExpect { status { isBadRequest() } }
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
}
