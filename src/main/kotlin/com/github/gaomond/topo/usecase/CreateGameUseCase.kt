package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.GameValidationException
import com.github.gaomond.topo.domain.model.AreaPreset
import com.github.gaomond.topo.domain.model.GameCreationCommand
import com.github.gaomond.topo.domain.model.GameCreationResult
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.ObjectType
import com.github.gaomond.topo.domain.port.GameRepositoryPort
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * ゲーム作成ユースケース（01-spec 1.1 のサーバー処理）。
 *
 * バリデーション → game INSERT（WAITING / 結果 NULL / creator NULL）→ creator player INSERT
 * （game_id・displayName 確定）→ game.creatorPlayerId UPDATE を 1 トランザクションで実行する。
 *
 * 依存は Domain ポート（[GameRepositoryPort] / [PlayerRepositoryPort]）のみで、
 * JPA 具象・PostGIS には依存しない（DIP / Clean Architecture）。
 */
@Service
class CreateGameUseCase(
    private val gameRepository: GameRepositoryPort,
    private val playerRepository: PlayerRepositoryPort,
) {
    /**
     * ゲームを作成し、発番した gameId / playerId を返す。
     *
     * @throws GameValidationException objectType 不正 / areaPreset 不正 / playerCount < 3
     */
    @Transactional
    fun create(command: GameCreationCommand): GameCreationResult {
        val objectType = resolveObjectType(command.objectType)
        val areaPreset = resolveAreaPreset(command.areaPresetKey)
        validatePlayerCount(command.playerCount)

        // playerId を先に発番する。displayName フォールバックが playerId 先頭 8 文字を使うため
        // （player INSERT より前に確定させる必要がある）。
        val gameId = UUID.randomUUID()
        val playerId = UUID.randomUUID()
        val displayName = resolveDisplayName(command.displayName, playerId)

        // 1. game を creatorPlayerId NULL / 結果 NULL / WAITING で作成
        gameRepository.createGame(
            gameId = gameId,
            status = GameStatus.WAITING,
            playerCount = command.playerCount,
            objectType = objectType.jsonValue,
            areaThreshold = areaPreset.sqm.toDouble(),
        )
        // 2. 作成者 player を作成（game_id・displayName を確定）
        playerRepository.createPlayer(
            playerId = playerId,
            gameId = gameId,
            displayName = displayName,
        )
        // 3. game.creatorPlayerId を作成者 player の id で埋める
        gameRepository.updateCreatorPlayerId(gameId = gameId, creatorPlayerId = playerId)

        return GameCreationResult(gameId = gameId, playerId = playerId)
    }

    private fun resolveObjectType(value: String): ObjectType =
        ObjectType.selectableFromJsonValueOrNull(value)
            ?: throw GameValidationException(
                GameValidationException.Reason.INVALID_OBJECT_TYPE,
                "objectType が不正です: $value",
            )

    private fun resolveAreaPreset(key: String): AreaPreset =
        AreaPreset.byKey(key)
            ?: throw GameValidationException(
                GameValidationException.Reason.INVALID_AREA_PRESET,
                "areaPreset が不正です: $key",
            )

    private fun validatePlayerCount(playerCount: Int) {
        if (playerCount < MIN_PLAYER_COUNT) {
            throw GameValidationException(
                GameValidationException.Reason.INVALID_PLAYER_COUNT,
                "playerCount は $MIN_PLAYER_COUNT 以上である必要があります: $playerCount",
            )
        }
    }

    /**
     * displayName を確定する（D6 案A）。null / 空 / 空白のみは playerId 先頭 8 文字にフォールバックする。
     */
    private fun resolveDisplayName(
        raw: String?,
        playerId: UUID,
    ): String {
        val trimmed = raw?.trim()
        return if (trimmed.isNullOrEmpty()) {
            playerId.toString().take(DISPLAY_NAME_FALLBACK_LENGTH)
        } else {
            trimmed
        }
    }

    private companion object {
        // 凸包成立に必要な最低点数（3 点）。
        const val MIN_PLAYER_COUNT = 3
        const val DISPLAY_NAME_FALLBACK_LENGTH = 8
    }
}
