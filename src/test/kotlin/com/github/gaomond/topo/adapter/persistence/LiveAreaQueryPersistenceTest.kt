package com.github.gaomond.topo.adapter.persistence

import com.github.gaomond.topo.adapter.persistence.jpa.GameJpaRepository
import com.github.gaomond.topo.adapter.persistence.jpa.PlayerJpaRepository
import com.github.gaomond.topo.domain.model.AreaPreset
import com.github.gaomond.topo.domain.model.Game
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.ObjectType
import com.github.gaomond.topo.domain.model.Player
import com.github.gaomond.topo.support.PostgisTestContainer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 実 PostGIS で測地面積と頂点列を検証する。
 * 3 点未満の null と共線 3 点の面積 0・線分を区別し、多角形の閉環と座標順を確認する。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LiveAreaQueryPersistenceTest {
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

    private fun adapter() = LiveAreaQueryAdapter(jdbcTemplate)

    private fun seedGame(): UUID {
        val gameId = UUID.randomUUID()
        gameRepository.createGame(Game.create(gameId, UUID.randomUUID(), 5, ObjectType.SHRINE, AreaPreset.ALL[1]))
        gameRepository.updateStatus(gameId, GameStatus.ACTIVE)
        gameRepository.flush()
        return gameId
    }

    private fun seedLivePlayer(
        gameId: UUID,
        lat: Double,
        lng: Double,
    ) {
        val playerId = UUID.randomUUID()
        playerRepository.createPlayer(Player.create(playerId, gameId, "プレイヤー"))
        playerRepository.flush()
        jdbcTemplate.update(
            "UPDATE player SET live_lat = ?, live_lng = ?, live_at = now() WHERE id = ?",
            lat,
            lng,
            playerId,
        )
    }

    private fun seedIdlePlayer(gameId: UUID) {
        val playerId = UUID.randomUUID()
        playerRepository.createPlayer(Player.create(playerId, gameId, "みそうしん"))
        playerRepository.flush()
    }

    @Test
    fun `test_currentLiveArea_非退化3点_正の測地面積と3頂点以上のhull`() {
        val gameId = seedGame()
        // 東京付近の直角三角形（約 0.01 度 ≒ 1km スケール）。
        seedLivePlayer(gameId, 35.68, 139.76)
        seedLivePlayer(gameId, 35.69, 139.76)
        seedLivePlayer(gameId, 35.68, 139.77)

        val area = adapter().currentLiveArea(gameId)

        assertTrue(area != null, "3 点以上なら非 null")
        assertTrue(area.sqm > 0.0, "非退化なら正の面積")
        // 都市スケールの三角形として m² オーダー（数十万 m²）に収まる緩い範囲。
        assertTrue(area.sqm in 100_000.0..2_000_000.0, "測地面積が m² オーダー: ${area.sqm}")
        assertTrue(area.hull.size >= 3, "多角形の頂点は 3 以上")
    }

    @Test
    fun `test_currentLiveArea_2点以下_null`() {
        val gameId = seedGame()
        seedLivePlayer(gameId, 35.68, 139.76)
        seedLivePlayer(gameId, 35.69, 139.77)

        assertNull(adapter().currentLiveArea(gameId), "live 点 < 3 は null")
    }

    @Test
    fun `test_currentLiveArea_一直線3点_sqm0かつ線分hull`() {
        val gameId = seedGame()
        // 緯度を固定した共線 3 点（同一 y=35.68・floating point でも厳密に一直線）→ 凸包は LineString、面積 0。
        seedLivePlayer(gameId, 35.68, 139.76)
        seedLivePlayer(gameId, 35.68, 139.77)
        seedLivePlayer(gameId, 35.68, 139.78)

        val area = adapter().currentLiveArea(gameId)

        assertTrue(area != null, "3 点あれば退化でも非 null（未計測とは区別）")
        assertEquals(0.0, area.sqm, "一直線は面積ゼロ")
        assertTrue(area.hull.isNotEmpty(), "退化でも成立する頂点列（線分）を返す")
    }

    @Test
    fun `test_currentLiveArea_live未送信を除外し全live参加者を対象`() {
        val gameId = seedGame()
        seedLivePlayer(gameId, 35.68, 139.76)
        seedLivePlayer(gameId, 35.69, 139.76)
        seedLivePlayer(gameId, 35.68, 139.77)
        // 未送信は対象外（在室判定では絞り込まない）。
        seedIdlePlayer(gameId)

        val area = adapter().currentLiveArea(gameId)

        assertTrue(area != null, "live を持つ 3 人が対象で非 null")
        assertTrue(area.sqm > 0.0)
    }

    @Test
    fun `test_currentLiveArea_2点live_1点未送信_null`() {
        val gameId = seedGame()
        seedLivePlayer(gameId, 35.68, 139.76)
        seedLivePlayer(gameId, 35.69, 139.77)
        // 未送信は COUNT に含まれないため live は 2 点＝ null。
        seedIdlePlayer(gameId)

        assertNull(adapter().currentLiveArea(gameId), "未送信を除くと 2 点で null")
    }

    @Test
    fun `test_currentLiveArea_hullが閉環かつ頂点順が妥当`() {
        val gameId = seedGame()
        seedLivePlayer(gameId, 35.68, 139.76)
        seedLivePlayer(gameId, 35.69, 139.76)
        seedLivePlayer(gameId, 35.68, 139.77)

        val hull = adapter().currentLiveArea(gameId)?.hull
        assertTrue(hull != null && hull.size >= 4, "多角形の頂点列は閉環のため 4 点以上（3 頂点 + 閉じ）")
        assertEquals(hull.first(), hull.last(), "閉環（先頭 = 末尾）")
        // 各頂点が投入した緯度経度レンジ内（lat/lng の取り違えがない）。
        assertTrue(hull.all { it.lat in 35.67..35.70 && it.lng in 139.75..139.78 })
    }
}
