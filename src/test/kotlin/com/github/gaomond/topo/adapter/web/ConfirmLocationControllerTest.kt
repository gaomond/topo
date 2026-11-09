package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.exception.InvalidCoordinateException
import com.github.gaomond.topo.domain.exception.PlayerNotFoundException
import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.usecase.ConfirmLocationInput
import com.github.gaomond.topo.usecase.ConfirmLocationUseCase
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
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.test.assertSame

/** 確定成功時の200と確定状態のJSON、不在・非所属の404、不正入力の400、確定不可の409を検証する。 */
@WebMvcTest(controllers = [ConfirmLocationController::class])
@Import(GameApiExceptionHandler::class)
class ConfirmLocationControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var confirmLocationUseCase: ConfirmLocationUseCase

    private val gameId = UUID.randomUUID()
    private val playerId = UUID.randomUUID()

    private fun path() = "/api/games/$gameId/players/$playerId/confirm"

    @org.junit.jupiter.api.BeforeEach
    fun setup() {
        whenever(
            confirmLocationUseCase.confirm(any()),
        ).thenReturn(
            com.github.gaomond.topo.usecase
                .ConfirmedLocationResult(playerId, true, java.time.Instant.parse("2026-11-08T01:00:00Z")),
        )
    }

    @Test
    fun test_confirmLocation_withValidCoordinate_returns200AndConfirmation() {
        mockMvc
            .post(path()) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lat":35.68,"lng":139.76}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.confirmed") { value(true) }
            }
        verify(confirmLocationUseCase).confirm(ConfirmLocationInput(gameId, playerId, Coordinate(35.68, 139.76)))
    }

    @Test
    fun test_confirmLocation_whenAlreadyConfirmed_returns409() {
        whenever(
            confirmLocationUseCase.confirm(any()),
        ).thenThrow(
            com.github.gaomond.topo.domain.exception
                .PlayerAlreadyConfirmedException(playerId),
        )
        mockMvc
            .post(path()) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lat":35.0,"lng":139.0}"""
            }.andExpect { status { isConflict() } }
    }

    @Test
    fun test_confirmLocation_whenNonActive_returns409() {
        whenever(
            confirmLocationUseCase.confirm(any()),
        ).thenThrow(
            com.github.gaomond.topo.domain.exception
                .GameConfirmationNotAllowedException(gameId),
        )
        mockMvc
            .post(path()) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lat":35.0,"lng":139.0}"""
            }.andExpect { status { isConflict() } }
    }

    @Test
    fun test_confirmLocation_whenPlayerNotFound_returns404() {
        whenever(confirmLocationUseCase.confirm(any()))
            .thenThrow(PlayerNotFoundException(gameId, playerId))
        mockMvc
            .post(path()) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lat":35.0,"lng":139.0}"""
            }.andExpect { status { isNotFound() } }
    }

    @Test
    fun test_confirmLocation_whenLatOutOfRange_returns400() {
        mockMvc
            .post(path()) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lat":100.0,"lng":139.0}"""
            }.andExpect {
                status { isBadRequest() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.reason") { value("INVALID_COORDINATE") }
                jsonPath("$.detail") { value("lat が範囲外です（-90..90）: 100.0") }
            }
        verify(confirmLocationUseCase, never()).confirm(any())
    }

    @Test
    fun test_confirmLocation_whenLngOutOfRange_returns400() {
        mockMvc
            .post(path()) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lat":35.0,"lng":200.0}"""
            }.andExpect {
                status { isBadRequest() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.reason") { value("INVALID_COORDINATE") }
                jsonPath("$.detail") { value("lng が範囲外です（-180..180）: 200.0") }
            }
        verify(confirmLocationUseCase, never()).confirm(any())
    }

    @Test
    fun test_confirmLocation_withNaN_returns400() {
        // JSON の NaN トークンは Jackson 既定で非数値として拒否され、パース失敗で 400 になる。
        mockMvc
            .post(path()) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lat":NaN,"lng":139.0}"""
            }.andExpect { status { isBadRequest() } }
        verify(confirmLocationUseCase, never()).confirm(any())
    }

    @Test
    fun test_confirmLocation_withMissingField_returns400() {
        // 非 null Double の欠損は Jackson/Kotlin モジュールがデシリアライズに失敗し 400 になる。
        mockMvc
            .post(path()) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lat":35.0}"""
            }.andExpect { status { isBadRequest() } }
        verify(confirmLocationUseCase, never()).confirm(any())
    }

    @Test
    fun test_confirmLocation_atCoordinateBoundary_returns200() {
        mockMvc
            .post(path()) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lat":-90.0,"lng":180.0}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.confirmed") { value(true) }
            }
        verify(confirmLocationUseCase).confirm(ConfirmLocationInput(gameId, playerId, Coordinate(-90.0, 180.0)))
    }

    @Test
    fun test_confirmLocation_whenUseCaseThrowsCoordinateException_propagatesSameException() {
        val failure = InvalidCoordinateException("復元した座標が不正です")
        whenever(confirmLocationUseCase.confirm(any())).thenThrow(failure)

        val ex =
            assertThrows<ServletException> {
                mockMvc.post(path()) {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"lat":35.0,"lng":139.0}"""
                }
            }
        assertSame(failure, ex.cause)
    }

    @Test
    fun test_confirmLocation_withNullCoordinate_returns400WithoutCallingUseCase() {
        mockMvc
            .post(path()) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lat":null,"lng":139.0}"""
            }.andExpect { status { isBadRequest() } }
        verify(confirmLocationUseCase, never()).confirm(any())
    }

    @Test
    fun test_confirmLocation_withMalformedJson_returns400WithoutCallingUseCase() {
        mockMvc
            .post(path()) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lat":35.0,"lng":}"""
            }.andExpect { status { isBadRequest() } }
        verify(confirmLocationUseCase, never()).confirm(any())
    }

    @Test fun test_confirmLocation_areaRejected_returns422WithReason() {
        for (reason in com.github.gaomond.topo.domain.exception.ConfirmationAreaRejectedException.Reason.entries) {
            whenever(
                confirmLocationUseCase.confirm(any()),
            ).thenThrow(
                com.github.gaomond.topo.domain.exception
                    .ConfirmationAreaRejectedException(reason),
            )
            mockMvc
                .post(path()) {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"lat":35.0,"lng":139.0}"""
                }.andExpect {
                    status { isUnprocessableEntity() }
                    content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
                    jsonPath("$.reason") { value(reason.name) }
                }
        }
    }

    @Test fun test_confirmLocation_final_returnsTypedResult() {
        val point = Coordinate(35.0, 139.0)
        val polygon = listOf(point, Coordinate(35.0, 139.001), Coordinate(35.001, 139.0), point)
        whenever(
            confirmLocationUseCase.confirm(any()),
        ).thenReturn(
            com.github.gaomond.topo.usecase.ConfirmedLocationResult(
                playerId,
                true,
                java.time.Instant.now(),
                com.github.gaomond.topo.domain.model
                    .GameResult(
                        50.0,
                        true,
                        polygon,
                        List(2) {
                            com.github.gaomond.topo.domain.model.GameObjectSnapshot(
                                9007199254740993L + it,
                                com.github.gaomond.topo.domain.model.ObjectType.SHRINE,
                                null,
                                polygon[0],
                            )
                        },
                    ),
            ),
        )
        mockMvc
            .post(path()) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lat":35.0,"lng":139.0}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.result.areaSqm") { value(50.0) }
                jsonPath("$.result.areaValid") { value(true) }
                jsonPath("$.result.objects[0].id") { value("9007199254740993") }
                jsonPath("$.result.objects[0].objectType") { value("shrine") }
                jsonPath("$.result.objects[0].name") { value(org.hamcrest.Matchers.nullValue()) }
                jsonPath("$.result.objects[0].lat") { value(35.0) }
                jsonPath("$.result.objects[0].lng") { value(139.0) }
                jsonPath("$.result.polygon.length()") { value(4) }
            }
    }
}
