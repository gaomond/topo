package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.GameNotFoundException
import com.github.gaomond.topo.domain.GameStartNotAllowedException
import com.github.gaomond.topo.domain.NotGameCreatorException
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.StartGameResult
import com.github.gaomond.topo.usecase.StartGameUseCase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.UUID

/**
 * US-06 Level 3（inbound コントローラ）検証。UseCase は [MockitoBean] で差し替える。
 * 200/403/404/409/400 マッピング・レスポンス形（gameId / status）を検証する。
 * テスト対象は src/ の [StartGameController] / [GameApiExceptionHandler] を import。
 */
@WebMvcTest(controllers = [StartGameController::class])
@Import(GameApiExceptionHandler::class)
class StartGameControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var startGameUseCase: StartGameUseCase

    private val gameId = UUID.randomUUID()
    private val playerId = UUID.randomUUID()

    @Test
    fun test_postStart_withCreator_returns200AndActiveStatus() {
        whenever(startGameUseCase.start(any(), any()))
            .thenReturn(StartGameResult(gameId, GameStatus.ACTIVE))

        mockMvc
            .post("/api/games/$gameId/start") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"playerId":"$playerId"}"""
            }.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.gameId") { value(gameId.toString()) }
                jsonPath("$.status") { value("ACTIVE") }
            }
    }

    @Test
    fun test_postStart_withUnknownGameId_returns404() {
        whenever(startGameUseCase.start(any(), any())).thenThrow(GameNotFoundException(gameId))
        mockMvc
            .post("/api/games/$gameId/start") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"playerId":"$playerId"}"""
            }.andExpect { status { isNotFound() } }
    }

    @Test
    fun test_postStart_withNonCreator_returns403() {
        whenever(startGameUseCase.start(any(), any())).thenThrow(NotGameCreatorException(gameId))
        mockMvc
            .post("/api/games/$gameId/start") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"playerId":"$playerId"}"""
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun test_postStart_whenUnderCapacity_returns409() {
        whenever(startGameUseCase.start(any(), any())).thenThrow(
            GameStartNotAllowedException(gameId, GameStartNotAllowedException.Reason.CAPACITY_NOT_REACHED),
        )
        mockMvc
            .post("/api/games/$gameId/start") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"playerId":"$playerId"}"""
            }.andExpect { status { isConflict() } }
    }

    @Test
    fun test_postStart_whenNotWaiting_returns409() {
        whenever(startGameUseCase.start(any(), any())).thenThrow(
            GameStartNotAllowedException(gameId, GameStartNotAllowedException.Reason.NOT_WAITING),
        )
        mockMvc
            .post("/api/games/$gameId/start") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"playerId":"$playerId"}"""
            }.andExpect { status { isConflict() } }
    }

    @Test
    fun test_postStart_withoutPlayerId_returns400() {
        mockMvc
            .post("/api/games/$gameId/start") {
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect { status { isBadRequest() } }
    }
}
