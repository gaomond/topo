package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.exception.GameValidationException
import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.model.Game
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.GameSummary
import com.github.gaomond.topo.domain.model.Player
import com.github.gaomond.topo.domain.model.PlayerReading
import com.github.gaomond.topo.domain.port.GameRepositoryPort
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 外部キー循環を避ける保存順、入力拒否、表示名補完、途中失敗の伝播を検証する。 */
class CreateGameUseCaseTest {
    private sealed interface Call {
        data class CreateGame(
            val gameId: UUID,
            val status: GameStatus,
            val playerCount: Int,
            val objectType: String,
            val areaThreshold: Double,
            val creatorPlayerId: UUID?,
        ) : Call

        data class CreatePlayer(
            val playerId: UUID,
            val gameId: UUID,
            val displayName: String,
        ) : Call

        data class UpdateCreator(
            val gameId: UUID,
            val creatorPlayerId: UUID,
        ) : Call
    }

    private class SpyGameRepository(
        private val calls: MutableList<Call>,
        private val failOnUpdate: Boolean = false,
    ) : GameRepositoryPort {
        override fun createGame(game: Game) {
            calls.add(
                Call.CreateGame(
                    game.gameId,
                    game.status,
                    game.playerCount,
                    game.objectType.jsonValue,
                    game.areaThreshold,
                    game.creatorPlayerId,
                ),
            )
        }

        override fun updateCreatorPlayerId(
            gameId: UUID,
            creatorPlayerId: UUID,
        ) {
            if (failOnUpdate) throw IllegalStateException("update failed")
            calls.add(Call.UpdateCreator(gameId, creatorPlayerId))
        }

        override fun updateStatus(
            gameId: UUID,
            status: GameStatus,
        ) = error("作成では呼ばれない")

        override fun findSummary(gameId: UUID): GameSummary? = error("作成では呼ばれない")

        override fun findByIdWithLock(gameId: UUID): Game? = error("作成では呼ばれない")
    }

    private class SpyPlayerRepository(
        private val calls: MutableList<Call>,
    ) : PlayerRepositoryPort {
        override fun createPlayer(player: Player) {
            calls.add(Call.CreatePlayer(player.playerId, player.gameId, player.displayName))
        }

        override fun countByGameId(gameId: UUID): Int = error("作成では呼ばれない")

        override fun findByGameId(gameId: UUID): List<PlayerReading> = error("作成では呼ばれない")

        override fun updateLiveLocation(
            gameId: UUID,
            playerId: UUID,
            coordinate: Coordinate,
            at: Instant,
        ): Boolean = error("作成では呼ばれない")
    }

    private fun useCase(
        calls: MutableList<Call>,
        failOnUpdate: Boolean = false,
    ): CreateGameUseCase =
        CreateGameUseCase(
            gameRepository = SpyGameRepository(calls, failOnUpdate),
            playerRepository = SpyPlayerRepository(calls),
        )

    private fun validCommand(
        objectType: String = "shrine",
        areaPresetKey: String = "medium",
        playerCount: Int = 3,
        displayName: String? = "たろう",
    ) = CreateGameInput(objectType, areaPresetKey, playerCount, displayName)

    @Test
    fun test_createGame_withValidInput_persistsGameThenCreatorThenUpdatesCreatorId() {
        val calls = mutableListOf<Call>()
        val result = useCase(calls).create(validCommand())

        assertEquals(3, calls.size)
        val create = calls[0] as Call.CreateGame
        val player = calls[1] as Call.CreatePlayer
        val update = calls[2] as Call.UpdateCreator

        assertEquals(GameStatus.WAITING, create.status)
        assertEquals(create.gameId, player.gameId)
        assertEquals(player.playerId, create.creatorPlayerId)
        assertEquals(player.playerId, update.creatorPlayerId)
        assertEquals(create.gameId, update.gameId)
        assertEquals(create.gameId, result.gameId)
        assertEquals(player.playerId, result.playerId)
    }

    @Test
    fun test_createGame_always_generatesDistinctGameIdAndPlayerId() {
        val calls = mutableListOf<Call>()
        val result = useCase(calls).create(validCommand())
        assertTrue(result.gameId != result.playerId)
    }

    @Test
    fun test_createGame_eachPreset_resolvesToExpectedAreaThreshold() {
        val expected = mapOf("small" to 500_000.0, "medium" to 2_000_000.0, "large" to 10_000_000.0)
        for ((key, sqm) in expected) {
            val calls = mutableListOf<Call>()
            useCase(calls).create(validCommand(areaPresetKey = key))
            val create = calls[0] as Call.CreateGame
            assertEquals(sqm, create.areaThreshold, "preset $key")
        }
    }

    @Test
    fun test_createGame_withValidInput_storesObjectTypeJsonValue() {
        val calls = mutableListOf<Call>()
        useCase(calls).create(validCommand(objectType = "shrine"))
        assertEquals("shrine", (calls[0] as Call.CreateGame).objectType)
    }

    @Test
    fun test_createGame_withInvalidObjectType_throwsValidationException() {
        val calls = mutableListOf<Call>()
        val ex =
            assertThrows<GameValidationException> {
                useCase(calls).create(validCommand(objectType = "mountain"))
            }
        assertEquals(GameValidationException.Reason.INVALID_OBJECT_TYPE, ex.reason)
        assertTrue(calls.isEmpty(), "バリデーション失敗時は永続化しない")
    }

    @Test
    fun test_createGame_withNonSelectableObjectType_throwsValidationException() {
        // enum に存在しても選択可能な種別でなければ拒否する。
        val calls = mutableListOf<Call>()
        val ex =
            assertThrows<GameValidationException> {
                useCase(calls).create(validCommand(objectType = "temple"))
            }
        assertEquals(GameValidationException.Reason.INVALID_OBJECT_TYPE, ex.reason)
    }

    @Test
    fun test_createGame_withInvalidAreaPreset_throwsValidationException() {
        val calls = mutableListOf<Call>()
        val ex =
            assertThrows<GameValidationException> {
                useCase(calls).create(validCommand(areaPresetKey = "huge"))
            }
        assertEquals(GameValidationException.Reason.INVALID_AREA_PRESET, ex.reason)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun test_createGame_withPlayerCountBelow3_throwsValidationException() {
        for (count in listOf(2, 0, -1)) {
            val calls = mutableListOf<Call>()
            val ex =
                assertThrows<GameValidationException>("playerCount=$count") {
                    useCase(calls).create(validCommand(playerCount = count))
                }
            assertEquals(GameValidationException.Reason.INVALID_PLAYER_COUNT, ex.reason)
            assertTrue(calls.isEmpty())
        }
    }

    @Test
    fun test_createGame_withPlayerCount3_isAccepted() {
        val calls = mutableListOf<Call>()
        useCase(calls).create(validCommand(playerCount = 3))
        assertEquals(3, (calls[0] as Call.CreateGame).playerCount)
    }

    @Test
    fun test_createGame_withDisplayName_persistsAsIs() {
        val calls = mutableListOf<Call>()
        useCase(calls).create(validCommand(displayName = "たろう"))
        assertEquals("たろう", (calls[1] as Call.CreatePlayer).displayName)
    }

    @Test
    fun test_createGame_withNullDisplayName_fallsBackToUuidPrefix8() {
        val calls = mutableListOf<Call>()
        useCase(calls).create(validCommand(displayName = null))
        val player = calls[1] as Call.CreatePlayer
        assertEquals(player.playerId.toString().take(8), player.displayName)
    }

    @Test
    fun test_createGame_withBlankDisplayName_fallsBackToUuidPrefix8() {
        for (blank in listOf("", "   ", "\t")) {
            val calls = mutableListOf<Call>()
            useCase(calls).create(validCommand(displayName = blank))
            val player = calls[1] as Call.CreatePlayer
            assertEquals(player.playerId.toString().take(8), player.displayName, "blank=[$blank]")
        }
    }

    @Test
    fun test_createGame_whenCreatorUpdateFails_propagatesException() {
        val calls = mutableListOf<Call>()
        assertThrows<IllegalStateException> {
            useCase(calls, failOnUpdate = true).create(validCommand())
        }
    }

    @Test
    fun test_createGame_multipleInvalidFields_preservesValidationOrder() {
        val calls = mutableListOf<Call>()
        val invalidType =
            assertThrows<GameValidationException> {
                useCase(calls).create(validCommand(objectType = "unknown", areaPresetKey = "unknown", playerCount = 2))
            }
        assertEquals(GameValidationException.Reason.INVALID_OBJECT_TYPE, invalidType.reason)
        val invalidPreset =
            assertThrows<GameValidationException> {
                useCase(calls).create(validCommand(areaPresetKey = "unknown", playerCount = 2))
            }
        assertEquals(GameValidationException.Reason.INVALID_AREA_PRESET, invalidPreset.reason)
        assertTrue(calls.isEmpty())
    }
}
