package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.domain.exception.GameNotFoundException
import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.model.CurrentArea
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.LiveLocation
import com.github.gaomond.topo.domain.model.PlayerSnapshot
import com.github.gaomond.topo.usecase.GetGameStateResult
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

/** 状態取得の 200・404 と、ライブ位置・在室判定・面積の JSON 表現を検証する。 */
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
            GetGameStateResult(
                gameId = gameId,
                status = GameStatus.WAITING,
                playerCount = 3,
                creatorPlayerId = p1,
                players =
                    listOf(
                        PlayerSnapshot(p1, "たろう", confirmed = true),
                        PlayerSnapshot(p2, "じろう", confirmed = false),
                    ),
                areaThreshold = 2_000_000.0,
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
                jsonPath("$.areaThreshold") { value(2000000.0) }
                jsonPath("$.players[0].playerId") { value(p1.toString()) }
                jsonPath("$.players[0].displayName") { value("たろう") }
                jsonPath("$.players[0].confirmed") { value(true) }
                jsonPath("$.players[1].confirmed") { value(false) }
            }
    }

    @Test
    fun test_getGame_responseIncludesCreatorPlayerId() {
        val creator = UUID.randomUUID()
        whenever(getGameStateUseCase.getState(any())).thenReturn(
            GetGameStateResult(
                gameId = gameId,
                status = GameStatus.WAITING,
                playerCount = 3,
                creatorPlayerId = creator,
                players = listOf(PlayerSnapshot(creator, "たろう", confirmed = false)),
                areaThreshold = 2_000_000.0,
            ),
        )

        mockMvc
            .get("/api/games/$gameId")
            .andExpect {
                status { isOk() }
                jsonPath("$.creatorPlayerId") { value(creator.toString()) }
            }
    }

    @Test
    fun test_getGame_withUnknownGameId_returns404() {
        whenever(getGameStateUseCase.getState(any())).thenThrow(GameNotFoundException(gameId))
        mockMvc
            .get("/api/games/$gameId")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `test_getGame_ACTIVEかつcurrentAreaあり_live_online_sqm_hullを含む`() {
        val p1 = UUID.randomUUID()
        val at = java.time.Instant.parse("2026-07-06T12:00:00Z")
        whenever(getGameStateUseCase.getState(any())).thenReturn(
            GetGameStateResult(
                gameId = gameId,
                status = GameStatus.ACTIVE,
                playerCount = 3,
                creatorPlayerId = p1,
                players =
                    listOf(
                        PlayerSnapshot(
                            p1,
                            "たろう",
                            confirmed = false,
                            live = LiveLocation(Coordinate(35.68, 139.76), at),
                            online = true,
                        ),
                    ),
                areaThreshold = 2_000_000.0,
                currentArea =
                    CurrentArea(
                        sqm = 1234567.0,
                        hull = listOf(Coordinate(35.68, 139.76), Coordinate(35.69, 139.77), Coordinate(35.68, 139.78)),
                    ),
            ),
        )

        mockMvc
            .get("/api/games/$gameId")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("ACTIVE") }
                jsonPath("$.players[0].online") { value(true) }
                jsonPath("$.players[0].live.lat") { value(35.68) }
                jsonPath("$.players[0].live.lng") { value(139.76) }
                jsonPath("$.players[0].live.at") { value("2026-07-06T12:00:00Z") }
                jsonPath("$.currentArea.sqm") { value(1234567.0) }
                jsonPath("$.currentArea.hull[0][0]") { value(35.68) }
                jsonPath("$.currentArea.hull[0][1]") { value(139.76) }
                jsonPath("$.currentArea.hull.length()") { value(3) }
            }
    }

    @Test
    fun `test_getGame_live未送信player_liveはnull_onlineはfalse`() {
        val p1 = UUID.randomUUID()
        whenever(getGameStateUseCase.getState(any())).thenReturn(
            GetGameStateResult(
                gameId = gameId,
                status = GameStatus.ACTIVE,
                playerCount = 3,
                creatorPlayerId = p1,
                players = listOf(PlayerSnapshot(p1, "たろう", confirmed = false, live = null, online = false)),
                areaThreshold = 2_000_000.0,
                currentArea = null,
            ),
        )

        mockMvc
            .get("/api/games/$gameId")
            .andExpect {
                status { isOk() }
                jsonPath("$.players[0].live") { value(null) }
                jsonPath("$.players[0].online") { value(false) }
            }
    }

    @Test
    fun `test_getGame_currentAreaがnull_JSONでcurrentAreaがnull`() {
        val p1 = UUID.randomUUID()
        whenever(getGameStateUseCase.getState(any())).thenReturn(
            GetGameStateResult(
                gameId = gameId,
                status = GameStatus.ACTIVE,
                playerCount = 3,
                creatorPlayerId = p1,
                players = listOf(PlayerSnapshot(p1, "たろう", confirmed = false)),
                areaThreshold = 2_000_000.0,
                currentArea = null,
            ),
        )

        mockMvc
            .get("/api/games/$gameId")
            .andExpect {
                status { isOk() }
                jsonPath("$.currentArea") { value(null) }
            }
    }

    @Test
    fun `test_getGame_resultは常にnull`() {
        val p1 = UUID.randomUUID()
        whenever(getGameStateUseCase.getState(any())).thenReturn(
            GetGameStateResult(gameId, GameStatus.WAITING, 3, p1, listOf(PlayerSnapshot(p1, "たろう", confirmed = false)), 2_000_000.0),
        )

        mockMvc
            .get("/api/games/$gameId")
            .andExpect {
                status { isOk() }
                jsonPath("$.result") { value(null) }
            }
    }

    @Test fun test_getGame_completed_returnsStoredResultAndNullCurrentArea() {
        val point = Coordinate(35.0, 139.0)
        val polygon = listOf(point, Coordinate(35.0, 139.001), Coordinate(35.001, 139.0), point)
        whenever(
            getGameStateUseCase.getState(any()),
        ).thenReturn(
            GetGameStateResult(
                gameId,
                GameStatus.COMPLETED,
                3,
                null,
                emptyList(),
                500000.0,
                result =
                    com.github.gaomond.topo.domain.model
                        .GameResult(50.0, true, 4, polygon),
            ),
        )
        mockMvc.get("/api/games/$gameId").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("COMPLETED") }
            jsonPath("$.currentArea") { value(org.hamcrest.Matchers.nullValue()) }
            jsonPath("$.result.areaSqm") { value(50.0) }
            jsonPath("$.result.objectCount") { value(4) }
            jsonPath("$.result.polygon[0].lat") { value(35.0) }
        }
    }
}
