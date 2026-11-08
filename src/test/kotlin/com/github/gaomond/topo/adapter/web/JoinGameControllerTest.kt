package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.exception.GameJoinNotAllowedException
import com.github.gaomond.topo.domain.exception.GameNotFoundException
import com.github.gaomond.topo.usecase.JoinGameResult
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

/** 参加の 201・404・409 と、参加者 ID の応答を検証する。 */
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
