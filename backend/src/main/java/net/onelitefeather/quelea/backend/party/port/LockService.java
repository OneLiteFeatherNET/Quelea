package net.onelitefeather.quelea.backend.party.port;

import java.util.function.Supplier;

public interface LockService {
    /** Runs action while holding the named lock; throws PartyException(LOCK_TIMEOUT) if not acquired within the wait. */
    <T> T withLock(String name, Supplier<T> action);
}
