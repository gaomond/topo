package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.GameNotFoundException
import com.github.gaomond.topo.domain.model.GameState
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.PlayerView
import com.github.gaomond.topo.usecase.GetGameStateUseCase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.UUID

/**
 * US-05 Level 3（inbound コントローラ）検証。UseCase は [MockitoBean] で差し替える。
 * 200（状態）/404 マッピング・レスポンス形を検証する。
 * テスト対象は src/ の [GameStateController] / [GameApiExceptionHandler] を import。
 */
@WebMvcTest(controllers = [GameStateController::class])
@Import(GameApiExceptionHandler::class)
class GameStateControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var getGameStateUseCase: GetGameStateUseCase

    private val gameId = UUID.randomUUID()

    @Test
    fun test_getGame_withExistingGame_returns200AndState() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        whenever(getGameStateUseCase.getState(any())).thenReturn(
            GameState(
                gameId = gameId,
                status = GameStatus.WAITING,
                playerCount = 3,
                players =
                    listOf(
                        PlayerView(p1, "たろう", confirmed = true),
                        PlayerView(p2, "じろう", confirmed = false),
                    ),
            ),
        )

        mockMvc
            .get("/api/games/$gameId")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.gameId") { value(gameId.toString()) }
                jsonPath("$.status") { value("WAITING") }
                jsonPath("$.playerCount") { value(3) }
                jsonPath("$.players[0].playerId") { value(p1.toString()) }
                jsonPath("$.players[0].displayName") { value("たろう") }
                jsonPath("$.players[0].confirmed") { value(true) }
                jsonPath("$.players[1].confirmed") { value(false) }
            }
    }

    @Test
    fun test_getGame_withUnknownGameId_returns404() {
        whenever(getGameStateUseCase.getState(any())).thenThrow(GameNotFoundException(gameId))
        mockMvc
            .get("/api/games/$gameId")
            .andExpect { status { isNotFound() } }
    }
}
