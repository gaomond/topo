package com.github.gaomond.topo.domain.model

import com.github.gaomond.topo.domain.exception.GameValidationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals

class GameCreationTest {
    @Test
    fun test_create_belowMinimum_rejectsPlayerCount() {
        for (count in listOf(2, 0, -1)) {
            val failure =
                assertThrows<GameValidationException> {
                    Game.create(UUID.randomUUID(), UUID.randomUUID(), count, ObjectType.SHRINE, AreaPreset.ALL[1])
                }
            assertEquals(GameValidationException.Reason.INVALID_PLAYER_COUNT, failure.reason)
        }
    }

    @Test
    fun test_create_validCount_startsWaitingWithSettingsAndCreator() {
        for (count in listOf(3, 4, Int.MAX_VALUE)) {
            val gameId = UUID.randomUUID()
            val creator = UUID.randomUUID()
            val game = Game.create(gameId, creator, count, ObjectType.SHRINE, AreaPreset.ALL[1])
            assertEquals(gameId, game.gameId)
            assertEquals(creator, game.creatorPlayerId)
            assertEquals(count, game.playerCount)
            assertEquals(GameStatus.WAITING, game.status)
            assertEquals(ObjectType.SHRINE, game.objectType)
            assertEquals(2_000_000.0, game.areaThreshold)
        }
    }

    @Test
    fun test_restore_savedStates_preservesSettingsWithoutResolvingPreset() {
        for (status in GameStatus.entries) {
            val creator = UUID.randomUUID()
            val game = Game.restore(UUID.randomUUID(), status, 4, creator, ObjectType.TEMPLE, 123.5)
            assertEquals(status, game.status)
            assertEquals(creator, game.creatorPlayerId)
            assertEquals(ObjectType.TEMPLE, game.objectType)
            assertEquals(123.5, game.areaThreshold)
        }
    }

    @Test
    fun test_createPlayer_rawNames_resolvesDisplayName() {
        val playerId = UUID.randomUUID()
        val gameId = UUID.randomUUID()
        for (raw in listOf(null, "", "  ", "\t")) {
            val player = Player.create(playerId, gameId, raw)
            assertEquals(playerId, player.playerId)
            assertEquals(gameId, player.gameId)
            assertEquals(playerId.toString().take(8), player.displayName)
        }
        assertEquals("たろう", Player.create(playerId, gameId, " たろう ").displayName)
    }

    @Test
    fun test_start_waitingGame_preservesSettingsAndOriginalState() {
        val creator = UUID.randomUUID()
        val game = Game.create(UUID.randomUUID(), creator, 4, ObjectType.SHRINE, AreaPreset.ALL[0])
        val started = game.start(creator) { 4 }
        assertEquals(GameStatus.WAITING, game.status)
        assertEquals(GameStatus.ACTIVE, started.status)
        assertEquals(game.gameId, started.gameId)
        assertEquals(game.creatorPlayerId, started.creatorPlayerId)
        assertEquals(game.playerCount, started.playerCount)
        assertEquals(game.objectType, started.objectType)
        assertEquals(game.areaThreshold, started.areaThreshold)
    }
}
