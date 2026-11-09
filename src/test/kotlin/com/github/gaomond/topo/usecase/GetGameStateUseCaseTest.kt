package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.exception.GameNotFoundException
import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.model.CurrentArea
import com.github.gaomond.topo.domain.model.Game
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.GameSummary
import com.github.gaomond.topo.domain.model.LiveLocation
import com.github.gaomond.topo.domain.model.Player
import com.github.gaomond.topo.domain.model.PlayerReading
import com.github.gaomond.topo.domain.model.Presence
import com.github.gaomond.topo.domain.port.GameRepositoryPort
import com.github.gaomond.topo.domain.port.LiveAreaQueryPort
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 時刻を固定して在室判定を検証し、進行中だけ暫定面積を問い合わせることを確認する。 */
class GetGameStateUseCaseTest {
    private class SpyGameRepository(
        private val summary: GameSummary?,
    ) : GameRepositoryPort {
        override fun createWithoutCreator(game: Game) = error("状態取得では呼ばれない")

        override fun updateCreatorPlayerId(game: Game) = error("状態取得では呼ばれない")

        override fun saveResult(game: Game) = error("unused")

        override fun updateStatus(game: Game) = error("状態取得では呼ばれない")

        override fun findSummary(gameId: UUID): GameSummary? = summary

        override fun findByIdWithLock(gameId: UUID): Game? = error("状態取得では呼ばれない")
    }

    private class StubPlayerRepository(
        private val players: List<PlayerReading>,
    ) : PlayerRepositoryPort {
        override fun findByGameIdAndPlayerId(
            gameId: UUID,
            playerId: UUID,
        ): Player? = error("この操作では呼ばれない")

        override fun confirmLocation(
            gameId: UUID,
            playerId: UUID,
            location: com.github.gaomond.topo.domain.model.ConfirmedLocation,
        ): Unit = error("この操作では呼ばれない")

        override fun createPlayer(player: Player) = error("状態取得では呼ばれない")

        override fun countByGameId(gameId: UUID): Int = error("状態取得では呼ばれない")

        override fun findByGameId(gameId: UUID): List<PlayerReading> = players

        override fun updateLiveLocation(
            gameId: UUID,
            playerId: UUID,
            coordinate: Coordinate,
            at: Instant,
        ): Boolean = error("状態取得では呼ばれない")
    }

    private class SpyLiveAreaQuery(
        private val area: CurrentArea?,
        var callCount: Int = 0,
    ) : LiveAreaQueryPort {
        override fun currentLiveArea(gameId: UUID): CurrentArea? {
            callCount++
            return area
        }
    }

    private val gameId = UUID.randomUUID()
    private val now = Instant.parse("2026-07-06T12:00:00Z")
    private val fixedClock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun useCase(
        summary: GameSummary?,
        players: List<PlayerReading> = emptyList(),
        liveArea: SpyLiveAreaQuery = SpyLiveAreaQuery(null),
    ) = GetGameStateUseCase(
        SpyGameRepository(summary),
        StubPlayerRepository(players),
        liveArea,
        fixedClock,
    )

    private fun reading(
        displayName: String,
        confirmed: Boolean = false,
        live: LiveLocation? = null,
    ) = PlayerReading(UUID.randomUUID(), displayName, confirmed, live)

    @Test
    fun test_getGameState_withExistingGame_returnsStatusPlayerCountAndPlayers() {
        val players = listOf(reading("たろう", confirmed = true), reading("じろう"))
        val state =
            useCase(
                GameSummary(GameStatus.WAITING, 3, creatorPlayerId = null, areaThreshold = 2_000_000.0),
                players,
            ).getState(GetGameStateInput(gameId))

        assertEquals(gameId, state.gameId)
        assertEquals(GameStatus.WAITING, state.status)
        assertEquals(3, state.playerCount)
        assertEquals(listOf("たろう", "じろう"), state.players.map { it.displayName })
    }

    @Test
    fun test_getGameState_withUnknownGameId_throwsNotFound() {
        assertThrows<GameNotFoundException> { useCase(summary = null).getState(GetGameStateInput(gameId)) }
    }

    @Test
    fun test_getGameState_returnsCreatorPlayerIdFromSummary() {
        val creator = UUID.randomUUID()
        val state =
            useCase(
                GameSummary(GameStatus.WAITING, 3, creatorPlayerId = creator, areaThreshold = 2_000_000.0),
            ).getState(GetGameStateInput(gameId))
        assertEquals(creator, state.creatorPlayerId)
    }

    @Test
    fun `test_getGameState_areaThresholdをsummaryから射影する`() {
        val summary = GameSummary(GameStatus.WAITING, 3, creatorPlayerId = null, areaThreshold = 500_000.0)
        val state = useCase(summary).getState(GetGameStateInput(gameId))
        assertEquals(500_000.0, state.areaThreshold)
    }

    @Test
    fun test_getGameState_reflectsConfirmedFlagFromPlayers() {
        val players = listOf(reading("かくてい", confirmed = true), reading("みかくてい", confirmed = false))
        val state =
            useCase(
                GameSummary(GameStatus.WAITING, 3, creatorPlayerId = null, areaThreshold = 2_000_000.0),
                players,
            ).getState(GetGameStateInput(gameId))

        assertTrue(state.players[0].confirmed)
        assertFalse(state.players[1].confirmed)
    }

    @Test
    fun `test_getState_ACTIVEかつlive3点以上_currentAreaを載せる`() {
        val area = CurrentArea(sqm = 1234.5, hull = listOf(Coordinate(35.0, 139.0)))
        val liveArea = SpyLiveAreaQuery(area)
        val state =
            useCase(
                GameSummary(GameStatus.ACTIVE, 3, creatorPlayerId = null, areaThreshold = 2_000_000.0),
                liveArea = liveArea,
            ).getState(GetGameStateInput(gameId))

        assertEquals(area, state.currentArea)
        assertEquals(1, liveArea.callCount)
    }

    @Test
    fun `test_getState_WAITING_currentAreaはnullで空間ポート未呼び出し`() {
        val liveArea = SpyLiveAreaQuery(CurrentArea(1.0, emptyList()))
        val state =
            useCase(
                GameSummary(GameStatus.WAITING, 3, creatorPlayerId = null, areaThreshold = 2_000_000.0),
                liveArea = liveArea,
            ).getState(GetGameStateInput(gameId))

        assertNull(state.currentArea)
        assertEquals(0, liveArea.callCount, "WAITING では空間クエリを発火しない")
    }

    @Test
    fun `test_getState_COMPLETED_currentAreaはnull`() {
        val liveArea = SpyLiveAreaQuery(CurrentArea(1.0, emptyList()))
        val state =
            useCase(
                GameSummary(GameStatus.COMPLETED, 3, creatorPlayerId = null, areaThreshold = 2_000_000.0),
                liveArea = liveArea,
            ).getState(GetGameStateInput(gameId))

        assertNull(state.currentArea)
        assertEquals(0, liveArea.callCount)
    }

    @Test
    fun `test_getState_liveAtがTTL内_onlineはtrue`() {
        val live = LiveLocation(Coordinate(35.0, 139.0), now.minus(Presence.TTL))
        val state =
            useCase(
                GameSummary(GameStatus.ACTIVE, 3, null, areaThreshold = 2_000_000.0),
                listOf(reading("たろう", live = live)),
            ).getState(GetGameStateInput(gameId))

        assertTrue(state.players[0].online)
        assertEquals(live, state.players[0].live)
    }

    @Test
    fun `test_getState_liveAtがTTL超過_onlineはfalse`() {
        val live = LiveLocation(Coordinate(35.0, 139.0), now.minus(Presence.TTL).minusMillis(1))
        val state =
            useCase(
                GameSummary(GameStatus.ACTIVE, 3, null, areaThreshold = 2_000_000.0),
                listOf(reading("たろう", live = live)),
            ).getState(GetGameStateInput(gameId))

        assertFalse(state.players[0].online)
    }

    @Test
    fun `test_getState_live未送信_onlineはfalseかつliveはnull`() {
        val state =
            useCase(
                GameSummary(GameStatus.ACTIVE, 3, null, areaThreshold = 2_000_000.0),
                listOf(reading("たろう", live = null)),
            ).getState(GetGameStateInput(gameId))

        assertFalse(state.players[0].online)
        assertNull(state.players[0].live)
    }

    @Test fun test_getGameState_completed_returnsStoredResultWithoutAreaQuery() {
        val polygon = listOf(Coordinate(35.0, 139.0), Coordinate(35.0, 139.001), Coordinate(35.001, 139.0), Coordinate(35.0, 139.0))
        val result =
            com.github.gaomond.topo.domain.model
                .GameResult(50.0, true, 3, polygon)
        val live = SpyLiveAreaQuery(null)
        val state =
            useCase(
                GameSummary(GameStatus.COMPLETED, 3, null, 500000.0, result),
                liveArea = live,
            ).getState(GetGameStateInput(gameId))
        assertEquals(result, state.result)
        assertNull(state.currentArea)
        assertEquals(0, live.callCount)
    }
}
