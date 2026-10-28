package net.onelitefeather.quelea.backend.support;

import net.onelitefeather.quelea.backend.party.port.LockService;
import net.onelitefeather.quelea.backend.problem.PartyException;
import net.onelitefeather.quelea.backend.problem.PartyProblem;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public final class InMemoryLockService implements LockService {

    private final Duration wait;
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public InMemoryLockService(Duration wait) {
        this.wait = wait;
    }

    @Override
    public <T> T withLock(String name, Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(name, key -> new ReentrantLock());
        boolean acquired;
        try {
            acquired = lock.tryLock(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PartyException(PartyProblem.LOCK_TIMEOUT, "Interrupted while waiting for " + name);
        }
        if (!acquired) {
            throw new PartyException(PartyProblem.LOCK_TIMEOUT, "Timed out waiting for " + name);
        }
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
