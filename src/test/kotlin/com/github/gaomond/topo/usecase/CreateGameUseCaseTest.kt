package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.GameValidationException
import com.github.gaomond.topo.domain.model.GameCreationCommand
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.port.GameRepositoryPort
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * US-04 Level 2（UseCase）検証。ポートは手書きの spy で差し替える（外部モックライブラリ非依存）。
 * 創成順序・バリデーション・displayName フォールバック・プリセット解決を検証する。
 * テスト対象は src/ の [CreateGameUseCase] を import（再定義しない）。
 */
class CreateGameUseCaseTest {
    // --- 呼び出しを記録する手書き spy ポート ---

    private sealed interface Call {
        data class CreateGame(
            val gameId: UUID,
            val status: GameStatus,
            val playerCount: Int,
            val objectType: String,
            val areaThreshold: Double,
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
        override fun createGame(
            gameId: UUID,
            status: GameStatus,
            playerCount: Int,
            objectType: String,
            areaThreshold: Double,
        ) {
            calls.add(Call.CreateGame(gameId, status, playerCount, objectType, areaThreshold))
        }

        override fun updateCreatorPlayerId(
            gameId: UUID,
            creatorPlayerId: UUID,
        ) {
            if (failOnUpdate) throw IllegalStateException("update failed")
            calls.add(Call.UpdateCreator(gameId, creatorPlayerId))
        }
    }

    private class SpyPlayerRepository(
        private val calls: MutableList<Call>,
    ) : PlayerRepositoryPort {
        override fun createPlayer(
            playerId: UUID,
            gameId: UUID,
            displayName: String,
        ) {
            calls.add(Call.CreatePlayer(playerId, gameId, displayName))
        }
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
    ) = GameCreationCommand(objectType, areaPresetKey, playerCount, displayName)

    // --- 正常系: 創成順序 ---

    @Test
    fun test_createGame_withValidInput_persistsGameThenCreatorThenUpdatesCreatorId() {
        val calls = mutableListOf<Call>()
        val result = useCase(calls).create(validCommand())

        assertEquals(3, calls.size)
        val create = calls[0] as Call.CreateGame
        val player = calls[1] as Call.CreatePlayer
        val update = calls[2] as Call.UpdateCreator

        // game は WAITING で作成される。
        assertEquals(GameStatus.WAITING, create.status)
        // player は同一 game に紐づく。
        assertEquals(create.gameId, player.gameId)
        // creator_player_id は作成した player の id で埋まる。
        assertEquals(player.playerId, update.creatorPlayerId)
        assertEquals(create.gameId, update.gameId)
        // 戻り値の gameId / playerId が永続化した値と一致する。
        assertEquals(create.gameId, result.gameId)
        assertEquals(player.playerId, result.playerId)
    }

    @Test
    fun test_createGame_always_generatesDistinctGameIdAndPlayerId() {
        val calls = mutableListOf<Call>()
        val result = useCase(calls).create(validCommand())
        assertTrue(result.gameId != result.playerId)
    }

    // --- プリセット解決 ---

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

    // --- バリデーション ---

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
        // enum には存在するが SELECTABLE 外は 400（検証範囲＝SELECTABLE のみ）。
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

    // --- displayName フォールバック（D6 案A） ---

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

    // --- 途中失敗の伝播（Tx ロールバックは Level 3 で検証） ---

    @Test
    fun test_createGame_whenCreatorUpdateFails_propagatesException() {
        val calls = mutableListOf<Call>()
        assertThrows<IllegalStateException> {
            useCase(calls, failOnUpdate = true).create(validCommand())
        }
    }
}
