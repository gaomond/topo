package com.github.gaomond.topo.adapter.persistence

import com.github.gaomond.topo.adapter.persistence.jpa.GameJpaRepository
import com.github.gaomond.topo.config.ClockConfig
import com.github.gaomond.topo.domain.exception.ConfirmationAreaRejectedException
import com.github.gaomond.topo.domain.exception.GameConfirmationNotAllowedException
import com.github.gaomond.topo.domain.model.Coordinate
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.model.ObjectType
import com.github.gaomond.topo.domain.port.HullServicePort
import com.github.gaomond.topo.support.PostgisTestContainer
import com.github.gaomond.topo.usecase.ConfirmLocationInput
import com.github.gaomond.topo.usecase.ConfirmLocationUseCase
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(
    ConfirmLocationUseCase::class,
    ClockConfig::class,
    PostgisHullService::class,
    GameAggregationPersistenceTest.Config::class,
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GameAggregationPersistenceTest {
    companion object {
        @JvmStatic @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            val c = PostgisTestContainer.instance
            registry.add("spring.datasource.url") { c.jdbcUrl }
            registry.add("spring.datasource.username") { c.username }
            registry.add("spring.datasource.password") { c.password }
        }
    }

    class ObservedArea(
        private val real: PostgisHullService,
    ) : HullServicePort by real {
        val failures = ConcurrentHashMap.newKeySet<List<Coordinate>>()
        val counts = ConcurrentHashMap<List<Coordinate>, AtomicInteger>()

        override fun countCoveredObjects(
            objectType: ObjectType,
            hull: List<Coordinate>,
        ): Int {
            counts.computeIfAbsent(hull) { AtomicInteger() }.incrementAndGet()
            if (failures.remove(hull)) error("集計障害")
            return real.countCoveredObjects(objectType, hull)
        }
    }

    class ObservedGames(
        private val real: GameJpaRepository,
    ) : com.github.gaomond.topo.domain.port.GameRepositoryPort by real {
        val failures = ConcurrentHashMap.newKeySet<UUID>()

        override fun saveResult(game: com.github.gaomond.topo.domain.model.Game) {
            real.saveResult(game)
            if (failures.remove(game.gameId)) error("結果保存障害")
        }
    }

    @TestConfiguration class Config {
        @Bean @Primary
        fun observedGames(real: GameJpaRepository) = ObservedGames(real)

        @Bean @Primary
        fun observedArea(real: PostgisHullService) = ObservedArea(real)
    }

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var games: GameJpaRepository

    @Autowired lateinit var confirm: ConfirmLocationUseCase

    @Autowired lateinit var areas: ObservedArea

    @Autowired lateinit var observedGames: ObservedGames

    @Autowired lateinit var realArea: PostgisHullService

    @Autowired lateinit var transactions: PlatformTransactionManager

    @org.junit.jupiter.api.BeforeEach fun resetObservations() {
        areas.counts.clear()
        areas.failures.clear()
    }

    private val a = Coordinate(35.0, 139.0)
    private val b = Coordinate(35.0, 139.001)
    private val c = Coordinate(35.001, 139.0)

    private fun seed(
        count: Int = 3,
        threshold: Double = 1_000_000.0,
    ): Pair<UUID, List<UUID>> {
        val game = UUID.randomUUID()
        val players = List(count) { UUID.randomUUID() }
        TransactionTemplate(transactions).executeWithoutResult {
            jdbc.update(
                "INSERT INTO game(id,status,player_count,object_type,area_threshold) VALUES (?,'ACTIVE',?,'shrine',?)",
                game,
                count,
                threshold,
            )
            players.forEach { jdbc.update("INSERT INTO player(id,game_id) VALUES (?,?)", it, game) }
        }
        return game to players
    }

    private fun run(
        game: UUID,
        player: UUID,
        point: Coordinate,
    ) = confirm.confirm(ConfirmLocationInput(game, player, point))

    private fun fixed(game: UUID): Int =
        jdbc.queryForObject("SELECT COUNT(*) FROM player WHERE game_id=? AND confirmed_at IS NOT NULL", Int::class.java, game)!!

    @Test fun test_calculateArea_degenerateAndDuplicate_countsPlayersWithoutLive() {
        val (game, p) = seed()
        assertEquals(1, realArea.calculateArea(game, a).confirmedPlayerCount)
        run(game, p[0], a)
        run(game, p[1], a)
        val area = realArea.calculateArea(game, b)
        assertEquals(3, area.confirmedPlayerCount)
        assertEquals(0.0, area.areaSqm)
        assertEquals(2, area.hull.size)
    }

    @Test fun test_confirm_finalCandidate_countsBoundaryAndPersistsSameHullAndArea() {
        val (game, p) = seed()
        assertNull(run(game, p[0], a).result)
        assertNull(run(game, p[1], b).result)
        val candidate = realArea.calculateArea(game, c)
        assertTrue(candidate.areaSqm in 5000.0..5100.0)
        val base = -(UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE)
        val objects = listOf(a, Coordinate(35.0001, 139.0001), Coordinate(36.0, 140.0), a)
        try {
            objects.forEachIndexed { index, point ->
                jdbc.update(
                    "INSERT INTO game_object(id,object_type,geom) VALUES (?,?,ST_SetSRID(ST_MakePoint(?,?),4326))",
                    base + index,
                    if (index == 3) "temple" else "shrine",
                    point.lng,
                    point.lat,
                )
            }
            val result = assertNotNull(run(game, p[2], c).result)
            assertEquals(2, result.objectCount)
            assertEquals(candidate.areaSqm, result.areaSqm)
            assertEquals(candidate.hull, result.polygon)
            assertEquals(1, areas.counts[result.polygon]?.get())
            val saved = assertNotNull(games.findSummary(game))
            assertEquals(GameStatus.COMPLETED, saved.status)
            assertEquals(result, saved.result)
            assertEquals("POLYGON", jdbc.queryForObject("SELECT GeometryType(polygon_geom) FROM game WHERE id=?", String::class.java, game))
            assertThrows<GameConfirmationNotAllowedException> { run(game, p[2], c) }
            assertEquals(1, areas.counts[result.polygon]?.get())
        } finally {
            objects.indices.forEach { jdbc.update("DELETE FROM game_object WHERE id=?", base + it) }
        }
    }

    @Test fun test_confirm_invalidArea_rejectsWithoutRecordingAndAllowsNewPoint() {
        val (game, p) = seed()
        run(game, p[0], a)
        run(game, p[1], b)
        assertEquals(
            ConfirmationAreaRejectedException.Reason.AREA_BELOW_MIN,
            assertThrows<ConfirmationAreaRejectedException> {
                run(game, p[2], a)
            }.reason,
        )
        assertEquals(2, fixed(game))
        assertNotNull(run(game, p[2], c).result)
        val (other, q) = seed(4, 100.0)
        run(other, q[0], a)
        run(other, q[1], b)
        assertEquals(
            ConfirmationAreaRejectedException.Reason.AREA_EXCEEDS_THRESHOLD,
            assertThrows<ConfirmationAreaRejectedException> {
                run(other, q[2], c)
            }.reason,
        )
        assertEquals(2, fixed(other))
    }

    @Test fun test_confirm_countFailure_rollsBackCandidateAndKeepsPreviousPoints() {
        val (game, p) = seed()
        run(game, p[0], a)
        run(game, p[1], b)
        areas.failures.add(realArea.calculateArea(game, c).hull)
        assertThrows<IllegalStateException> { run(game, p[2], c) }
        TransactionTemplate(transactions).executeWithoutResult {
            assertEquals(2, fixed(game))
            val state = assertNotNull(games.findSummary(game))
            assertEquals(GameStatus.ACTIVE, state.status)
            assertNull(state.result)
        }
        assertNotNull(run(game, p[2], c).result)
    }

    private fun <T> parallel(
        first: () -> T,
        second: () -> T,
    ): List<Result<T>> {
        val pool = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val tasks =
                listOf(first, second).map { operation ->
                    pool.submit<Result<T>> {
                        ready.countDown()
                        check(start.await(5, TimeUnit.SECONDS))
                        runCatching(operation)
                    }
                }
            check(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            return tasks.map { it.get(15, TimeUnit.SECONDS) }
        } finally {
            start.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun test_confirm_twoRemainingPlayers_serializesAndAggregatesOnlyOnce() {
        val (game, p) = seed()
        run(game, p[0], a)
        val replies = parallel({ run(game, p[1], b) }, { run(game, p[2], c) })
        assertTrue(replies.all { it.isSuccess })
        assertEquals(1, replies.count { it.getOrThrow().result != null })
        val result = replies.mapNotNull { it.getOrThrow().result }.single()
        assertEquals(1, areas.counts[result.polygon]?.get())
        assertEquals(result, games.findSummary(game)?.result)
    }

    @Test fun test_confirm_duplicateFinalPlayer_recordsOneResultWithoutOverwrite() {
        val (game, p) = seed()
        run(game, p[0], a)
        run(game, p[1], b)
        val replies = parallel({ run(game, p[2], c) }, { run(game, p[2], c) })
        assertEquals(1, replies.count { it.isSuccess })
        assertIs<GameConfirmationNotAllowedException>(replies.single { it.isFailure }.exceptionOrNull())
        assertEquals(3, fixed(game))
        val result = assertNotNull(replies.single { it.isSuccess }.getOrThrow().result)
        assertEquals(1, areas.counts[result.polygon]?.get())
    }

    @Test fun test_confirm_resultSaveFailure_rollsBackFlushedCompletionAndPoint() {
        val (game, p) = seed()
        run(game, p[0], a)
        run(game, p[1], b)
        observedGames.failures.add(game)
        assertThrows<IllegalStateException> { run(game, p[2], c) }
        TransactionTemplate(transactions).executeWithoutResult {
            assertEquals(2, fixed(game))
            val state = assertNotNull(games.findSummary(game))
            assertEquals(GameStatus.ACTIVE, state.status)
            assertNull(state.result)
            assertNull(jdbc.queryForMap("SELECT polygon_geom FROM game WHERE id=?", game)["polygon_geom"])
        }
    }

    @Test fun test_confirm_fourPlayersWithInnerPoint_completesByPlayerCount() {
        val (game, p) = seed(4)
        run(game, p[0], a)
        run(game, p[1], b)
        run(game, p[2], c)
        val result = assertNotNull(run(game, p[3], Coordinate(35.0001, 139.0001)).result)
        assertEquals(4, result.polygon.size)
        assertEquals(4, fixed(game))
        assertEquals(GameStatus.COMPLETED, games.findSummary(game)?.status)
    }
}
