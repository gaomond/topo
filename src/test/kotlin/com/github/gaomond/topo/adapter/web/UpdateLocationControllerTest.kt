package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.exception.GameValidationException
import com.github.gaomond.topo.domain.exception.PlayerNotFoundException
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

/**
 * US-07 Level 3（inbound コントローラ）検証。UseCase は [MockitoBean] で差し替える。
 * 204（ボディなし）/ 404（不在・非所属）/ 400（範囲外・NaN・欠損）マッピングを検証する。
 * テスト対象は src/ の [UpdateLocationController] / [GameApiExceptionHandler] を import。
 */
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
        verify(updateLiveLocationUseCase).update(gameId, playerId, 35.68, 139.76)
    }

    @Test
    fun test_updateLocation_whenPlayerNotFound_returns404() {
        whenever(updateLiveLocationUseCase.update(any(), any(), any(), any()))
            .thenThrow(PlayerNotFoundException(gameId, playerId))
        mockMvc
            .put(path()) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lat":35.0,"lng":139.0}"""
            }.andExpect { status { isNotFound() } }
    }

    @Test
    fun test_updateLocation_whenLatOutOfRange_returns400() {
        whenever(updateLiveLocationUseCase.update(any(), any(), any(), any()))
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
        whenever(updateLiveLocationUseCase.update(any(), any(), any(), any()))
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
        // UseCase まで到達しないため呼び出しは発生しない。
        mockMvc
            .put(path()) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lat":NaN,"lng":139.0}"""
            }.andExpect { status { isBadRequest() } }
        verify(updateLiveLocationUseCase, never()).update(any(), any(), any(), any())
    }

    @Test
    fun test_updateLocation_withMissingField_returns400() {
        // 非 null Double の欠損は Jackson/Kotlin モジュールがデシリアライズに失敗し 400 になる。
        mockMvc
            .put(path()) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lat":35.0}"""
            }.andExpect { status { isBadRequest() } }
        verify(updateLiveLocationUseCase, never()).update(any(), any(), any(), any())
    }
}
