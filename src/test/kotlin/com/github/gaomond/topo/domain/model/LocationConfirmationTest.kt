package com.github.gaomond.topo.domain.model

import com.github.gaomond.topo.domain.exception.GameConfirmationNotAllowedException
import com.github.gaomond.topo.domain.exception.PlayerAlreadyConfirmedException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocationConfirmationTest {
    private val gameId = UUID.randomUUID()
    private val playerId = UUID.randomUUID()
    private val at = Instant.parse("2026-11-08T12:00:00Z")

    @Test
    fun test_requireCanConfirm_active_accepts() {
        Game.restore(gameId, GameStatus.ACTIVE, 3, playerId, ObjectType.SHRINE, 500000.0).requireCanConfirm()
    }

    @Test
    fun test_requireCanConfirm_notActive_rejects() {
        for (status in listOf(GameStatus.WAITING, GameStatus.COMPLETED)) {
            assertThrows<GameConfirmationNotAllowedException> {
                Game.restore(gameId, status, 3, playerId, ObjectType.SHRINE, 500000.0).requireCanConfirm()
            }
        }
    }

    @Test
    fun test_confirmLocation_unconfirmed_returnsConfirmedPlayerWithoutChangingOriginal() {
        val player = Player.create(playerId, gameId, "テスト")
        val coordinate = Coordinate(35.0, 139.0)
        val confirmed = player.confirmLocation(coordinate, at)
        assertEquals(ConfirmedLocation(coordinate, at), confirmed.confirmedLocation)
        assertNull(player.confirmedLocation)
    }

    @Test
    fun test_confirmLocation_restoredConfirmation_rejectsWithoutOverwrite() {
        val original = ConfirmedLocation(Coordinate(35.0, 139.0), at)
        val player = Player.restore(playerId, gameId, "テスト", original)
        assertThrows<PlayerAlreadyConfirmedException> { player.confirmLocation(Coordinate(36.0, 140.0), at.plusSeconds(1)) }
        assertEquals(original, player.confirmedLocation)
    }
}
