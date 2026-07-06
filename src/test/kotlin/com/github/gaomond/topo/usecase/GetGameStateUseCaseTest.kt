package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.exception.GameNotFoundException
import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.model.CurrentArea
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.GameSummary
import com.github.gaomond.topo.domain.model.LiveLocation
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

/**
 * US-05 / US-08 Level 2（UseCase）検証。状態取得・404・players 射影に加え、
 * US-08 の online（TTL 判定）と currentArea 組み立て（ACTIVE 限定・空間ポート発火）を検証する。
 * ポート・Clock は手書きの stub/spy で固定する。テスト対象は src/ の [GetGameStateUseCase] を import。
 */
class GetGameStateUseCaseTest {
    private class SpyGameRepository(
        private val summary: GameSummary?,
    ) : GameRepositoryPort {
        override fun createGame(
            gameId: UUID,
            status: GameStatus,
            playerCount: Int,
            objectType: String,
            areaThreshold: Double,
        ) = error("状態取得では呼ばれない")

        override fun updateCreatorPlayerId(
            gameId: UUID,
            creatorPlayerId: UUID,
        ) = error("状態取得では呼ばれない")

        override fun updateStatus(
            gameId: UUID,
            status: GameStatus,
        ) = error("状態取得では呼ばれない")

        override fun findSummary(gameId: UUID): GameSummary? = summary
    }

    private class StubPlayerRepository(
        private val players: List<PlayerReading>,
    ) : PlayerRepositoryPort {
        override fun createPlayer(
            playerId: UUID,
            gameId: UUID,
            displayName: String,
        ) = error("状態取得では呼ばれない")

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
        val state = useCase(GameSummary(GameStatus.WAITING, 3, creatorPlayerId = null), players).getState(gameId)

        assertEquals(gameId, state.gameId)
        assertEquals(GameStatus.WAITING, state.status)
        assertEquals(3, state.playerCount)
        assertEquals(listOf("たろう", "じろう"), state.players.map { it.displayName })
    }

    @Test
    fun test_getGameState_withUnknownGameId_throwsNotFound() {
        assertThrows<GameNotFoundException> { useCase(summary = null).getState(gameId) }
    }

    @Test
    fun test_getGameState_returnsCreatorPlayerIdFromSummary() {
        val creator = UUID.randomUUID()
        val state = useCase(GameSummary(GameStatus.WAITING, 3, creatorPlayerId = creator)).getState(gameId)
        assertEquals(creator, state.creatorPlayerId)
    }

    @Test
    fun test_getGameState_reflectsConfirmedFlagFromPlayers() {
        val players = listOf(reading("かくてい", confirmed = true), reading("みかくてい", confirmed = false))
        val state = useCase(GameSummary(GameStatus.WAITING, 3, creatorPlayerId = null), players).getState(gameId)

        assertTrue(state.players[0].confirmed)
        assertFalse(state.players[1].confirmed)
    }

    @Test
    fun `test_getState_ACTIVEかつlive3点以上_currentAreaを載せる`() {
        val area = CurrentArea(sqm = 1234.5, hull = listOf(Coordinate(35.0, 139.0)))
        val liveArea = SpyLiveAreaQuery(area)
        val state = useCase(GameSummary(GameStatus.ACTIVE, 3, creatorPlayerId = null), liveArea = liveArea).getState(gameId)

        assertEquals(area, state.currentArea)
        assertEquals(1, liveArea.callCount)
    }

    @Test
    fun `test_getState_WAITING_currentAreaはnullで空間ポート未呼び出し`() {
        val liveArea = SpyLiveAreaQuery(CurrentArea(1.0, emptyList()))
        val state = useCase(GameSummary(GameStatus.WAITING, 3, creatorPlayerId = null), liveArea = liveArea).getState(gameId)

        assertNull(state.currentArea)
        assertEquals(0, liveArea.callCount, "WAITING では空間クエリを発火しない")
    }

    @Test
    fun `test_getState_COMPLETED_currentAreaはnull`() {
        val liveArea = SpyLiveAreaQuery(CurrentArea(1.0, emptyList()))
        val state = useCase(GameSummary(GameStatus.COMPLETED, 3, creatorPlayerId = null), liveArea = liveArea).getState(gameId)

        assertNull(state.currentArea)
        assertEquals(0, liveArea.callCount)
    }

    @Test
    fun `test_getState_liveAtがTTL内_onlineはtrue`() {
        val live = LiveLocation(Coordinate(35.0, 139.0), now.minus(Presence.TTL))
        val state = useCase(GameSummary(GameStatus.ACTIVE, 3, null), listOf(reading("たろう", live = live))).getState(gameId)

        assertTrue(state.players[0].online)
        assertEquals(live, state.players[0].live)
    }

    @Test
    fun `test_getState_liveAtがTTL超過_onlineはfalse`() {
        val live = LiveLocation(Coordinate(35.0, 139.0), now.minus(Presence.TTL).minusMillis(1))
        val state = useCase(GameSummary(GameStatus.ACTIVE, 3, null), listOf(reading("たろう", live = live))).getState(gameId)

        assertFalse(state.players[0].online)
    }

    @Test
    fun `test_getState_live未送信_onlineはfalseかつliveはnull`() {
        val state = useCase(GameSummary(GameStatus.ACTIVE, 3, null), listOf(reading("たろう", live = null))).getState(gameId)

        assertFalse(state.players[0].online)
        assertNull(state.players[0].live)
    }
}
