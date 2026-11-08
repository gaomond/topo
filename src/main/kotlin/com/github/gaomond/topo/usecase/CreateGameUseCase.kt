package com.github.gaomond.topo.usecase

import com.github.gaomond.topo.domain.exception.GameValidationException
import com.github.gaomond.topo.domain.model.AreaPreset
import com.github.gaomond.topo.domain.model.DisplayName
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.ObjectType
import com.github.gaomond.topo.domain.port.GameRepositoryPort
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * ゲームと作成者を同一トランザクションで保存する。
 *
 * 外部キーの循環を避けるため、ゲーム作成 → 作成者保存 → 作成者 ID の設定の順に行う。
 * 途中で失敗した場合は全体をロールバックする。
 */
@Service
class CreateGameUseCase(
    private val gameRepository: GameRepositoryPort,
    private val playerRepository: PlayerRepositoryPort,
) {
    /**
     * ゲームを作成する。
     *
     * @throws GameValidationException 種別・面積プリセットが無効、または参加人数が 3 未満。
     */
    @Transactional
    fun create(input: CreateGameInput): CreateGameResult {
        val objectType = resolveObjectType(input.objectType)
        val areaPreset = resolveAreaPreset(input.areaPresetKey)
        validatePlayerCount(input.playerCount)

        // 表示名のフォールバックに playerId を使うため、保存前に発番する。
        val gameId = UUID.randomUUID()
        val playerId = UUID.randomUUID()
        val displayName = DisplayName.resolve(input.displayName, playerId)

        gameRepository.createGame(
            gameId = gameId,
            status = GameStatus.WAITING,
            playerCount = input.playerCount,
            objectType = objectType.jsonValue,
            areaThreshold = areaPreset.sqm.toDouble(),
        )
        playerRepository.createPlayer(
            playerId = playerId,
            gameId = gameId,
            displayName = displayName,
        )
        gameRepository.updateCreatorPlayerId(gameId = gameId, creatorPlayerId = playerId)

        return CreateGameResult(gameId = gameId, playerId = playerId)
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

    private companion object {
        // 凸包成立に必要な最低点数（3 点）。
        const val MIN_PLAYER_COUNT = 3
    }
}
