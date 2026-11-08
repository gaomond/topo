package com.github.gaomond.topo.adapter.persistence

import com.github.gaomond.topo.domain.exception.GameJoinNotAllowedException
import com.github.gaomond.topo.domain.exception.GameStartNotAllowedException
import com.github.gaomond.topo.domain.model.GameStatus
import com.github.gaomond.topo.domain.port.GameRepositoryPort
import com.github.gaomond.topo.domain.port.PlayerRepositoryPort
import com.github.gaomond.topo.support.PostgisTestContainer
import com.github.gaomond.topo.usecase.CreateGameInput
import com.github.gaomond.topo.usecase.CreateGameResult
import com.github.gaomond.topo.usecase.CreateGameUseCase
import com.github.gaomond.topo.usecase.JoinGameInput
import com.github.gaomond.topo.usecase.JoinGameUseCase
import com.github.gaomond.topo.usecase.StartGameInput
import com.github.gaomond.topo.usecase.StartGameUseCase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(
    CreateGameUseCase::class,
    JoinGameUseCase::class,
    StartGameUseCase::class,
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GameMembershipConcurrencyTest {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            val container = PostgisTestContainer.instance
            registry.add("spring.datasource.url") { container.jdbcUrl }
            registry.add("spring.datasource.username") { container.username }
            registry.add("spring.datasource.password") { container.password }
        }
    }

    @Autowired
    private lateinit var createGame: CreateGameUseCase

    @Autowired
    private lateinit var joinGame: JoinGameUseCase

    @Autowired
    private lateinit var startGame: StartGameUseCase

    @Autowired
    private lateinit var games: GameRepositoryPort

    @Autowired
    private lateinit var players: PlayerRepositoryPort

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var transactions: PlatformTransactionManager

    @Test
    fun test_join_whenTwoRequestsCompeteForLastSlot_onlyFirstSucceeds() {
        val game = gameWithOneSlot()
        val (first, second) = overlap({ join(game) }, { join(game) })

        first.getOrThrow()
        val rejected = assertIs<GameJoinNotAllowedException>(second.exceptionOrNull())
        assertEquals(GameJoinNotAllowedException.Reason.CAPACITY_REACHED, rejected.reason)
        assertGame(game, GameStatus.WAITING)
    }

    @Test
    fun test_start_whenLastJoinHoldsLock_startsAfterJoinCommits() {
        val game = gameWithOneSlot()
        val (first, second) = overlap({ join(game) }, { start(game) })

        first.getOrThrow()
        second.getOrThrow()
        assertGame(game, GameStatus.ACTIVE)
    }

    @Test
    fun test_join_whenUnderCapacityStartHoldsLock_joinsAfterStartIsRejected() {
        val game = gameWithOneSlot()
        val (first, second) = overlap({ start(game) }, { join(game) })

        val rejected = assertIs<GameStartNotAllowedException>(first.exceptionOrNull())
        assertEquals(GameStartNotAllowedException.Reason.CAPACITY_NOT_REACHED, rejected.reason)
        second.getOrThrow()
        assertGame(game, GameStatus.WAITING)
    }

    private fun gameWithOneSlot(): CreateGameResult = createGame.create(CreateGameInput("shrine", "small", 3, null)).also { join(it) }

    private fun join(game: CreateGameResult) {
        joinGame.join(JoinGameInput(game.gameId))
    }

    private fun start(game: CreateGameResult) {
        startGame.start(StartGameInput(game.gameId, game.playerId))
    }

    private fun assertGame(
        game: CreateGameResult,
        status: GameStatus,
    ) {
        assertEquals(3, players.countByGameId(game.gameId))
        assertEquals(status, games.findSummary(game.gameId)?.status)
    }

    private fun overlap(
        first: () -> Unit,
        second: () -> Unit,
    ): Pair<Result<Unit>, Result<Unit>> {
        val executor = Executors.newFixedThreadPool(2)
        val firstReady = CompletableFuture<Int>()
        val secondReady = CompletableFuture<Int>()
        val releaseFirst = CountDownLatch(1)
        val transaction = TransactionTemplate(transactions)
        try {
            val firstResult =
                executor.submit<Result<Unit>> {
                    requireNotNull(
                        transaction.execute { status ->
                            val pid = backendPid()
                            val outcome = runCatching(first)
                            if (outcome.isFailure) status.setRollbackOnly()
                            firstReady.complete(pid)
                            assertTrue(releaseFirst.await(10, SECONDS))
                            outcome
                        },
                    )
                }
            val firstPid = firstReady.get(5, SECONDS)
            val secondResult =
                executor.submit<Result<Unit>> {
                    runCatching {
                        transaction.executeWithoutResult {
                            secondReady.complete(backendPid())
                            second()
                        }
                    }
                }
            val secondPid = secondReady.get(5, SECONDS)
            assertBlockedBy(secondPid, firstPid)
            releaseFirst.countDown()
            return firstResult.get(5, SECONDS) to secondResult.get(5, SECONDS)
        } finally {
            releaseFirst.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(10, SECONDS))
        }
    }

    private fun backendPid(): Int = requireNotNull(jdbc.queryForObject("SELECT pg_backend_pid()", Int::class.java))

    private fun assertBlockedBy(
        blockedPid: Int,
        blockingPid: Int,
    ) {
        val deadline = TimeSource.Monotonic.markNow() + 5.seconds
        while (!deadline.hasPassedNow()) {
            val blocked =
                jdbc.queryForObject(
                    "SELECT ? = ANY(pg_blocking_pids(?))",
                    Boolean::class.java,
                    blockingPid,
                    blockedPid,
                )
            if (blocked == true) return
            Thread.sleep(20)
        }
        error("The second request did not wait for the first transaction")
    }
}
