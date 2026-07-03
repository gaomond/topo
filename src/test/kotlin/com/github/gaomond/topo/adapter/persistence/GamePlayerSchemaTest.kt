package com.github.gaomond.topo.adapter.persistence

import com.github.gaomond.topo.adapter.persistence.jpa.GameJpaEntity
import com.github.gaomond.topo.adapter.persistence.jpa.GameJpaRepository
import com.github.gaomond.topo.adapter.persistence.jpa.PlayerJpaEntity
import com.github.gaomond.topo.adapter.persistence.jpa.PlayerJpaRepository
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.support.PostgisTestContainer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * US-00 スキーマ・永続化の Level 3 検証。
 * 実 PostGIS（Testcontainers）に対し Flyway を適用し、enum/FK/NULL 許容/循環解消/空間型/
 * 上限なし/JPA CRUD を検証する。テスト対象は src/ から import（プロダクションの再定義はしない）。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GamePlayerSchemaTest {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            val c = PostgisTestContainer.instance
            registry.add("spring.datasource.url") { c.jdbcUrl }
            registry.add("spring.datasource.username") { c.username }
            registry.add("spring.datasource.password") { c.password }
        }
    }

    @Autowired
    private lateinit var gameRepository: GameJpaRepository

    @Autowired
    private lateinit var playerRepository: PlayerJpaRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    // --- ヘルパ: 最小の game / player を生 SQL で作る ---

    private fun insertGameRaw(
        id: UUID = UUID.randomUUID(),
        status: String = "WAITING",
        playerCount: Int = 3,
        creatorPlayerId: UUID? = null,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO game (id, status, player_count, object_type, area_threshold, creator_player_id)
            VALUES (?, ?::game_status, ?, 'shrine', 2000000, ?)
            """.trimIndent(),
            id,
            status,
            playerCount,
            creatorPlayerId,
        )
    }

    private fun insertPlayerRaw(
        id: UUID = UUID.randomUUID(),
        gameId: UUID,
    ) {
        jdbcTemplate.update(
            "INSERT INTO player (id, game_id) VALUES (?, ?)",
            id,
            gameId,
        )
    }

    // --- マイグレーション再現性 ---

    @Test
    fun test_flywayMigration_onCleanDb_createsGamePlayerAndEnum() {
        // コンテナ起動＝Flyway 成功。enum 型・テーブルの存在をカタログで確認する。
        val gameStatusValues =
            jdbcTemplate.queryForList(
                """
                SELECT e.enumlabel
                FROM pg_type t
                JOIN pg_enum e ON e.enumtypid = t.oid
                WHERE t.typname = 'game_status'
                ORDER BY e.enumsortorder
                """.trimIndent(),
                String::class.java,
            )
        assertEquals(listOf("WAITING", "ACTIVE", "COMPLETED"), gameStatusValues)

        val tables =
            jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('game', 'player')",
                String::class.java,
            )
        assertEquals(setOf("game", "player"), tables.toSet())
    }

    // --- enum 制約 ---

    @Test
    fun test_insertGame_withInvalidStatus_failsByEnumConstraint() {
        assertThrows<DataIntegrityViolationException> {
            insertGameRaw(status = "PAUSED")
        }
    }

    // --- FK 整合 ---

    @Test
    fun test_insertPlayer_withNonExistentGameId_failsByFk() {
        assertThrows<DataIntegrityViolationException> {
            insertPlayerRaw(gameId = UUID.randomUUID())
        }
    }

    @Test
    fun test_updateGame_withNonExistentCreatorPlayerId_failsByFk() {
        val gameId = UUID.randomUUID()
        insertGameRaw(id = gameId)
        assertThrows<DataIntegrityViolationException> {
            jdbcTemplate.update(
                "UPDATE game SET creator_player_id = ? WHERE id = ?",
                UUID.randomUUID(),
                gameId,
            )
        }
    }

    // --- NULL 許容 ---

    @Test
    fun test_insertGame_withNullableResultColumns_succeeds() {
        val gameId = UUID.randomUUID()
        insertGameRaw(id = gameId)
        val nulls =
            jdbcTemplate.queryForMap(
                "SELECT creator_player_id, polygon_geom, area_sqm, area_valid, object_count FROM game WHERE id = ?",
                gameId,
            )
        assertNull(nulls["creator_player_id"])
        assertNull(nulls["polygon_geom"])
        assertNull(nulls["area_sqm"])
        assertNull(nulls["area_valid"])
        assertNull(nulls["object_count"])
    }

    @Test
    fun test_insertPlayer_withNullableSpatialAndLiveColumns_succeeds() {
        val gameId = UUID.randomUUID()
        insertGameRaw(id = gameId)
        val playerId = UUID.randomUUID()
        insertPlayerRaw(id = playerId, gameId = gameId)
        val nulls =
            jdbcTemplate.queryForMap(
                "SELECT display_name, live_lat, live_lng, live_at, fixed_geom, confirmed_at FROM player WHERE id = ?",
                playerId,
            )
        assertNull(nulls["display_name"])
        assertNull(nulls["live_lat"])
        assertNull(nulls["live_lng"])
        assertNull(nulls["live_at"])
        assertNull(nulls["fixed_geom"])
        assertNull(nulls["confirmed_at"])
    }

    // --- 循環解消（1.6 の 3 ステップ）---

    @Test
    fun test_createGameThenCreatorThenUpdate_inOrder_resolvesCircularReference() {
        // 1. game を creator_player_id NULL で作成
        val gameId = UUID.randomUUID()
        insertGameRaw(id = gameId)
        // 2. 作成者 player を作成（game_id を埋める）
        val creatorId = UUID.randomUUID()
        insertPlayerRaw(id = creatorId, gameId = gameId)
        // 3. game.creator_player_id を UPDATE
        val updated =
            jdbcTemplate.update(
                "UPDATE game SET creator_player_id = ? WHERE id = ?",
                creatorId,
                gameId,
            )
        assertEquals(1, updated)
        val storedCreator =
            jdbcTemplate.queryForObject(
                "SELECT creator_player_id FROM game WHERE id = ?",
                UUID::class.java,
                gameId,
            )
        assertEquals(creatorId, storedCreator)
    }

    // --- 空間型 ---

    @Test
    fun test_insertSpatialGeoms_with4326PointAndPolygon_storesAndReads() {
        val gameId = UUID.randomUUID()
        insertGameRaw(id = gameId)
        // polygon_geom（Polygon,4326）を生 SQL で格納
        jdbcTemplate.update(
            "UPDATE game SET polygon_geom = ST_GeomFromText('POLYGON((139.0 35.0,139.1 35.0,139.1 35.1,139.0 35.1,139.0 35.0))', 4326) WHERE id = ?",
            gameId,
        )
        val polygonInfo =
            jdbcTemplate.queryForMap(
                "SELECT ST_SRID(polygon_geom) AS srid, GeometryType(polygon_geom) AS gtype FROM game WHERE id = ?",
                gameId,
            )
        assertEquals(4326, polygonInfo["srid"])
        assertEquals("POLYGON", polygonInfo["gtype"])

        // fixed_geom（Point,4326）を生 SQL で格納
        val playerId = UUID.randomUUID()
        insertPlayerRaw(id = playerId, gameId = gameId)
        jdbcTemplate.update(
            "UPDATE player SET fixed_geom = ST_SetSRID(ST_MakePoint(139.05, 35.05), 4326) WHERE id = ?",
            playerId,
        )
        val pointInfo =
            jdbcTemplate.queryForMap(
                "SELECT ST_SRID(fixed_geom) AS srid, GeometryType(fixed_geom) AS gtype FROM player WHERE id = ?",
                playerId,
            )
        assertEquals(4326, pointInfo["srid"])
        assertEquals("POINT", pointInfo["gtype"])
    }

    // --- 上限なし（エッジ）---

    @Test
    fun test_insertGame_withLargePlayerCount_isNotRejected() {
        val gameId = UUID.randomUUID()
        insertGameRaw(id = gameId, playerCount = 100)
        val stored =
            jdbcTemplate.queryForObject(
                "SELECT player_count FROM game WHERE id = ?",
                Int::class.java,
                gameId,
            )
        assertEquals(100, stored)
    }

    // --- JPA CRUD ラウンドトリップ ---

    @Test
    fun test_saveAndFind_gameEntity_roundTripsNonSpatialColumns() {
        val id = UUID.randomUUID()
        val createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val saved =
            gameRepository.save(
                GameJpaEntity(
                    id = id,
                    status = GameStatus.ACTIVE,
                    playerCount = 3,
                    objectType = "shrine",
                    areaThreshold = 2_000_000.0,
                    creatorPlayerId = null,
                    createdAt = createdAt,
                    areaSqm = null,
                    areaValid = null,
                    objectCount = null,
                ),
            )
        gameRepository.flush()

        val found = gameRepository.findById(saved.id).orElseThrow()
        assertEquals(GameStatus.ACTIVE, found.status)
        assertEquals(3, found.playerCount)
        assertEquals("shrine", found.objectType)
        assertEquals(2_000_000.0, found.areaThreshold)
        assertNull(found.creatorPlayerId)
        assertNull(found.areaSqm)
        assertNull(found.areaValid)
        assertNull(found.objectCount)
    }

    @Test
    fun test_saveAndFind_playerEntity_roundTripsNonSpatialColumns() {
        val gameId = UUID.randomUUID()
        gameRepository.save(
            GameJpaEntity(
                id = gameId,
                status = GameStatus.WAITING,
                playerCount = 3,
                objectType = "shrine",
                areaThreshold = 500_000.0,
            ),
        )
        val playerId = UUID.randomUUID()
        val savedPlayer =
            playerRepository.save(
                PlayerJpaEntity(
                    id = playerId,
                    gameId = gameId,
                    displayName = "alice",
                    liveLat = 35.68,
                    liveLng = 139.76,
                    liveAt = Instant.now().truncatedTo(ChronoUnit.MILLIS),
                ),
            )
        playerRepository.flush()

        val found = playerRepository.findById(savedPlayer.id).orElseThrow()
        assertEquals(gameId, found.gameId)
        assertEquals("alice", found.displayName)
        assertEquals(35.68, found.liveLat)
        assertEquals(139.76, found.liveLng)
        assertNotNull(found.liveAt)
        assertNull(found.confirmedAt)
    }
}
