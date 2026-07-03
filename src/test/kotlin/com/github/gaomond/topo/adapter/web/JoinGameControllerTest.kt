package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.GameJoinNotAllowedException
import com.github.gaomond.topo.domain.GameNotFoundException
import com.github.gaomond.topo.domain.model.JoinGameResult
import com.github.gaomond.topo.usecase.JoinGameUseCase
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
 * US-05 Level 3（inbound コントローラ）検証。UseCase は [MockitoBean] で差し替える。
 * 201/404/409 マッピング・レスポンス形（playerId のみ）を検証する。
 * テスト対象は src/ の [JoinGameController] / [GameApiExceptionHandler] を import。
 */
@WebMvcTest(controllers = [JoinGameController::class])
@Import(GameApiExceptionHandler::class)
class JoinGameControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var joinGameUseCase: JoinGameUseCase

    private val gameId = UUID.randomUUID()

    @Test
    fun test_postPlayers_withValidBody_returns201AndPlayerId() {
        val playerId = UUID.randomUUID()
        whenever(joinGameUseCase.join(any())).thenReturn(JoinGameResult(playerId))

        mockMvc
            .post("/api/games/$gameId/players") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"displayName":"じろう"}"""
            }.andExpect {
                status { isCreated() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.playerId") { value(playerId.toString()) }
            }
    }

    @Test
    fun test_postPlayers_withoutDisplayName_returns201() {
        whenever(joinGameUseCase.join(any())).thenReturn(JoinGameResult(UUID.randomUUID()))
        mockMvc
            .post("/api/games/$gameId/players") {
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect { status { isCreated() } }
    }

    @Test
    fun test_postPlayers_withUnknownGameId_returns404() {
        whenever(joinGameUseCase.join(any())).thenThrow(GameNotFoundException(gameId))
        mockMvc
            .post("/api/games/$gameId/players") {
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect { status { isNotFound() } }
    }

    @Test
    fun test_postPlayers_whenNotWaiting_returns409() {
        whenever(joinGameUseCase.join(any())).thenThrow(
            GameJoinNotAllowedException(gameId, GameJoinNotAllowedException.Reason.NOT_WAITING),
        )
        mockMvc
            .post("/api/games/$gameId/players") {
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect { status { isConflict() } }
    }

    @Test
    fun test_postPlayers_whenCapacityReached_returns409() {
        whenever(joinGameUseCase.join(any())).thenThrow(
            GameJoinNotAllowedException(gameId, GameJoinNotAllowedException.Reason.CAPACITY_REACHED),
        )
        mockMvc
            .post("/api/games/$gameId/players") {
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect { status { isConflict() } }
    }
}
