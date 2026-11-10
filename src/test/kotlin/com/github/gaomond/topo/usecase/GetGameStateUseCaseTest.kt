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
import com.github.gaomond.topo.domain.port.CurrentAreaQueryPort
import com.github.gaomond.topo.domain.port.GameRepositoryPort
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

        var findCalls: Int = 0

        override fun findByGameId(gameId: UUID): List<PlayerReading> {
            findCalls++
            return players
        }

        override fun updateLiveLocation(
            gameId: UUID,
            playerId: UUID,
            coordinate: Coordinate,
            at: Instant,
        ): Boolean = error("状態取得では呼ばれない")
    }

    private class SpyCurrentAreaQuery(
        private val area: CurrentArea?,
        var callCount: Int = 0,
    ) : CurrentAreaQueryPort {
        override fun currentArea(gameId: UUID): CurrentArea? {
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
        currentArea: SpyCurrentAreaQuery = SpyCurrentAreaQuery(null),
    ) = GetGameStateUseCase(
        SpyGameRepository(summary),
        StubPlayerRepository(players),
        currentArea,
        fixedClock,
    )

    private fun reading(
        displayName: String,
        confirmed: Boolean = false,
        live: LiveLocation? = null,
    ) = PlayerReading(
        UUID.randomUUID(),
        displayName,
        if (confirmed) {
            com.github.gaomond.topo.domain.model
                .ConfirmedLocation(Coordinate(35.59, 139.60), now)
        } else {
            null
        },
        live,
    )

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
    fun `test_getState_ACTIVEかつ採用位置3人分以上_currentAreaを載せる`() {
        val area = CurrentArea(sqm = 1234.5, hull = listOf(Coordinate(35.0, 139.0)))
        val currentArea = SpyCurrentAreaQuery(area)
        val state =
            useCase(
                GameSummary(GameStatus.ACTIVE, 3, creatorPlayerId = null, areaThreshold = 2_000_000.0),
                currentArea = currentArea,
            ).getState(GetGameStateInput(gameId))

        assertEquals(area, state.currentArea)
        assertEquals(1, currentArea.callCount)
    }

    @Test
    fun `test_getState_WAITING_currentAreaはnullで空間ポート未呼び出し`() {
        val currentArea = SpyCurrentAreaQuery(CurrentArea(1.0, emptyList()))
        val state =
            useCase(
                GameSummary(GameStatus.WAITING, 3, creatorPlayerId = null, areaThreshold = 2_000_000.0),
                currentArea = currentArea,
            ).getState(GetGameStateInput(gameId))

        assertNull(state.currentArea)
        assertEquals(0, currentArea.callCount, "WAITING では空間クエリを発火しない")
    }

    @Test
    fun `test_getState_COMPLETED_currentAreaはnull`() {
        val currentArea = SpyCurrentAreaQuery(CurrentArea(1.0, emptyList()))
        val state =
            useCase(
                GameSummary(GameStatus.COMPLETED, 3, creatorPlayerId = null, areaThreshold = 2_000_000.0),
                currentArea = currentArea,
            ).getState(GetGameStateInput(gameId))

        assertNull(state.currentArea)
        assertEquals(0, currentArea.callCount)
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
                .GameResult(
                    50.0,
                    true,
                    polygon,
                    List(3) {
                        com.github.gaomond.topo.domain.model.GameObjectSnapshot(
                            it.toLong(),
                            com.github.gaomond.topo.domain.model.ObjectType.SHRINE,
                            null,
                            polygon[0],
                        )
                    },
                )
        val live = SpyCurrentAreaQuery(null)
        val state =
            useCase(
                GameSummary(GameStatus.COMPLETED, 3, null, 500000.0, result),
                currentArea = live,
            ).getState(GetGameStateInput(gameId))
        assertEquals(result, state.result)
        assertNull(state.currentArea)
        assertEquals(0, live.callCount)
    }

    @Test
    fun test_getState_allStatuses_preservesFixedPointWithoutUsingConfirmationForPresence() {
        val fixed =
            com.github.gaomond.topo.domain.model
                .ConfirmedLocation(Coordinate(35.59, 139.60), now)
        for (status in GameStatus.entries) {
            val stale = LiveLocation(Coordinate(35.7, 139.8), now.minusSeconds(11))
            val players =
                listOf(
                    PlayerReading(UUID.randomUUID(), "離席", fixed, stale),
                    PlayerReading(UUID.randomUUID(), "liveなし", fixed, null),
                    PlayerReading(UUID.randomUUID(), "未確定", null, LiveLocation(Coordinate(35.0, 139.0), now)),
                )
            val repository = StubPlayerRepository(players)
            val state =
                GetGameStateUseCase(
                    SpyGameRepository(GameSummary(status, 3, null, 500000.0)),
                    repository,
                    SpyCurrentAreaQuery(null),
                    fixedClock,
                ).getState(GetGameStateInput(gameId))
            assertEquals(1, repository.findCalls)
            assertEquals(listOf(fixed, fixed, null), state.players.map { it.confirmedLocation })
            assertEquals(listOf(true, true, false), state.players.map { it.confirmed })
            assertEquals(listOf(false, false, true), state.players.map { it.online })
            assertEquals(stale, state.players[0].live)
        }
    }

    @Test
    fun test_getState_activeNullOrZero_returnsPortValueOnceWithoutRecalculation() {
        for (area in listOf(null, CurrentArea(0.0, listOf(Coordinate(35.68, 139.76))))) {
            val query = SpyCurrentAreaQuery(area)
            val state =
                useCase(GameSummary(GameStatus.ACTIVE, 3, null, 500000.0), currentArea = query)
                    .getState(GetGameStateInput(gameId))
            assertEquals(area, state.currentArea)
            assertEquals(1, query.callCount)
        }
    }
}
