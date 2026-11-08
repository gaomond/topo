package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.exception.GameValidationException
import com.github.gaomond.topo.usecase.CreateGameInput
import com.github.gaomond.topo.usecase.CreateGameResult
import com.github.gaomond.topo.usecase.CreateGameUseCase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.UUID

/** 作成の 201・400 と、応答に発番 ID を含み URL を含めないことを検証する。 */
@WebMvcTest(controllers = [CreateGameController::class])
@Import(GameApiExceptionHandler::class)
class CreateGameControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var createGameUseCase: CreateGameUseCase

    private val validBody =
        """
        {"objectType":"shrine","areaPreset":"medium","playerCount":3,"displayName":"たろう"}
        """.trimIndent()

    @Test
    fun test_postGames_withValidBody_returns201AndGamePlayerIds() {
        val gameId = UUID.randomUUID()
        val playerId = UUID.randomUUID()
        whenever(createGameUseCase.create(any()))
            .thenReturn(CreateGameResult(gameId = gameId, playerId = playerId))

        mockMvc
            .post("/api/games") {
                contentType = MediaType.APPLICATION_JSON
                content = validBody
            }.andExpect {
                status { isCreated() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.gameId") { value(gameId.toString()) }
                jsonPath("$.playerId") { value(playerId.toString()) }
                jsonPath("$.inviteUrl") { doesNotExist() }
            }
    }

    @Test
    fun test_postGames_withoutDisplayName_returns201() {
        whenever(createGameUseCase.create(any()))
            .thenReturn(CreateGameResult(UUID.randomUUID(), UUID.randomUUID()))
        mockMvc
            .post("/api/games") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"objectType":"shrine","areaPreset":"medium","playerCount":3}"""
            }.andExpect {
                status { isCreated() }
            }
    }

    @Test
    fun test_postGames_withInvalidObjectType_returns400() {
        whenever(createGameUseCase.create(any())).thenThrow(
            GameValidationException(GameValidationException.Reason.INVALID_OBJECT_TYPE, "bad type"),
        )
        mockMvc
            .post("/api/games") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"objectType":"mountain","areaPreset":"medium","playerCount":3}"""
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun test_postGames_withInvalidAreaPreset_returns400() {
        whenever(createGameUseCase.create(any())).thenThrow(
            GameValidationException(GameValidationException.Reason.INVALID_AREA_PRESET, "bad preset"),
        )
        mockMvc
            .post("/api/games") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"objectType":"shrine","areaPreset":"huge","playerCount":3}"""
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun test_postGames_withPlayerCountBelow3_returns400() {
        whenever(createGameUseCase.create(any())).thenThrow(
            GameValidationException(GameValidationException.Reason.INVALID_PLAYER_COUNT, "too few"),
        )
        for (count in listOf(2, 0, -1)) {
            mockMvc
                .post("/api/games") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"objectType":"shrine","areaPreset":"medium","playerCount":$count}"""
                }.andExpect { status { isBadRequest() } }
        }
    }

    @Test
    fun test_postGames_passesRequestFieldsToUseCaseAsCommand() {
        whenever(
            createGameUseCase.create(
                eq(CreateGameInput("shrine", "medium", 3, "たろう")),
            ),
        ).thenReturn(CreateGameResult(UUID.randomUUID(), UUID.randomUUID()))

        mockMvc
            .post("/api/games") {
                contentType = MediaType.APPLICATION_JSON
                content = validBody
            }.andExpect { status { isCreated() } }
    }
}
