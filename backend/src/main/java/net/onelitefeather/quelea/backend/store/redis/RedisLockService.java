package net.onelitefeather.quelea.backend.store.redis;

import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import net.onelitefeather.quelea.backend.party.port.LockService;
import net.onelitefeather.quelea.backend.problem.PartyException;
import net.onelitefeather.quelea.backend.problem.PartyProblem;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

/**
 * Named locks in Dragonfly: {@code SET lock:<name> <token> NX PX <ttl>}. The lock is retried until acquired or
 * the wait is over, and released only by the owner (compare-and-delete). A holder that stalls past the TTL
 * loses the lock, which is the accepted trade-off of the design.
 */
@Singleton
@Requires(beans = StatefulRedisConnection.class)
public class RedisLockService implements LockService {

    private static final long BACKOFF_MIN_MILLIS = 10;
    private static final long BACKOFF_MAX_MILLIS = 50;

    private final RedisCommands<String, String> commands;
    private final RedisKeys keys;
    private final Duration lockTtl;
    private final Duration lockWait;

    public RedisLockService(StatefulRedisConnection<String, String> connection,
                            RedisKeys keys,
                            @Value("${quelea.redis.lock-ttl:3s}") Duration lockTtl,
                            @Value("${quelea.redis.lock-wait:1s}") Duration lockWait) {
        this.commands = connection.sync();
        this.keys = keys;
        this.lockTtl = lockTtl;
        this.lockWait = lockWait;
    }

    @Override
    public <T> T withLock(String name, Supplier<T> action) {
        String key = keys.lock(name);
        String token = UUID.randomUUID().toString();
        acquire(key, token, name);
        try {
            return action.get();
        } finally {
            RedisScripts.compareAndDelete(commands, key, token);
        }
    }

    private void acquire(String key, String token, String name) {
        long deadline = System.nanoTime() + lockWait.toNanos();
        while (!tryAcquire(key, token)) {
            if (System.nanoTime() >= deadline) {
                throw new PartyException(PartyProblem.LOCK_TIMEOUT, "Timed out waiting for " + name);
            }
            long backoffMillis = ThreadLocalRandom.current().nextLong(BACKOFF_MIN_MILLIS, BACKOFF_MAX_MILLIS + 1);
            LockSupport.parkNanos(Duration.ofMillis(backoffMillis).toNanos());
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                throw new PartyException(PartyProblem.LOCK_TIMEOUT, "Interrupted while waiting for " + name);
            }
        }
    }

    private boolean tryAcquire(String key, String token) {
        return "OK".equals(commands.set(key, token, SetArgs.Builder.nx().px(lockTtl.toMillis())));
    }
}
