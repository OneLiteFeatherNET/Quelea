package net.onelitefeather.quelea.backend.store.redis;

import io.lettuce.core.api.sync.RedisCommands;
import net.onelitefeather.quelea.backend.problem.PartyException;
import net.onelitefeather.quelea.backend.problem.PartyProblem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
class RedisLockServiceTest {

    private static final String NAME = "party:test";

    private static DragonflyFixture dragonfly;

    private RedisKeys keys;
    private RedisCommands<String, String> commands;

    @BeforeAll
    static void startDragonfly() {
        dragonfly = DragonflyFixture.start();
    }

    @AfterAll
    static void stopDragonfly() {
        if (dragonfly != null) {
            dragonfly.close();
        }
    }

    @BeforeEach
    void freshState() {
        dragonfly.flushAll();
        keys = new RedisKeys("quelea:");
        commands = dragonfly.connection().sync();
    }

    @Test
    void withLockReturnsTheResultOfTheAction() {
        RedisLockService locks = locks(Duration.ofMillis(100));

        assertEquals("done", locks.withLock(NAME, () -> "done"));
    }

    @Test
    void lockIsReleasedAfterTheAction() {
        RedisLockService locks = locks(Duration.ofMillis(100));

        locks.withLock(NAME, () -> null);

        assertNull(commands.get(keys.lock(NAME)));
        assertEquals("second", locks.withLock(NAME, () -> "second"));
    }

    @Test
    void lockIsReleasedWhenTheActionThrows() {
        RedisLockService locks = locks(Duration.ofMillis(100));

        assertThrows(IllegalStateException.class, () -> locks.withLock(NAME, () -> {
            throw new IllegalStateException("boom");
        }));

        assertEquals("next", locks.withLock(NAME, () -> "next"));
    }

    @Test
    void secondCallerTimesOutWhileAnotherHolderKeepsTheLock() throws Exception {
        RedisLockService locks = locks(Duration.ofMillis(100));
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService holder = Executors.newSingleThreadExecutor();
        try {
            Future<?> running = holder.submit(() -> locks.withLock(NAME, () -> {
                held.countDown();
                awaitQuietly(release);
                return null;
            }));
            assertTrue(held.await(2, TimeUnit.SECONDS), "the holder should acquire the lock");

            PartyException timeout = assertThrows(PartyException.class,
                    () -> locks.withLock(NAME, () -> "never"));

            assertEquals(PartyProblem.LOCK_TIMEOUT, timeout.problem());
            release.countDown();
            running.get(2, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            holder.shutdownNow();
        }
    }

    @Test
    void releaseDoesNotDeleteALockOwnedByAnotherToken() {
        RedisLockService locks = locks(Duration.ofMillis(100));

        locks.withLock(NAME, () -> {
            // Simulates the lock expiring and being taken over by another holder while the action runs.
            commands.set(keys.lock(NAME), "someone-else");
            return null;
        });

        assertEquals("someone-else", commands.get(keys.lock(NAME)));
    }

    @Test
    void concurrentCallersNeverRunTheirActionsAtTheSameTime() throws Exception {
        RedisLockService locks = locks(Duration.ofSeconds(2));
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxInside = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < 2; t++) {
                futures.add(pool.submit(() -> {
                    awaitQuietly(start);
                    for (int i = 0; i < 20; i++) {
                        locks.withLock(NAME, () -> {
                            maxInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
                            inside.decrementAndGet();
                            return null;
                        });
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, maxInside.get(), "at most one holder may be inside the lock at a time");
    }

    private RedisLockService locks(Duration wait) {
        return new RedisLockService(dragonfly.connection(), keys, Duration.ofSeconds(3), wait);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
