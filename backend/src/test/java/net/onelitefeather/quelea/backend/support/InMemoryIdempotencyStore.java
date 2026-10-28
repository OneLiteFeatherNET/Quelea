package net.onelitefeather.quelea.backend.support;

import net.onelitefeather.quelea.backend.idempotency.IdempotencyRecord;
import net.onelitefeather.quelea.backend.idempotency.IdempotencyStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Idempotency records in memory, expiring on the injected clock. Operations are synchronized: it is a test fake. */
public final class InMemoryIdempotencyStore implements IdempotencyStore {

    private record Scope(String client, String key) {
    }

    private record Entry(IdempotencyRecord record, Instant expiresAt) {
    }

    private final Clock clock;
    private final Map<Scope, Entry> entries = new HashMap<>();

    public InMemoryIdempotencyStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public synchronized Optional<IdempotencyRecord> claim(String client, String key, IdempotencyRecord marker, Duration ttl) {
        Scope scope = new Scope(client, key);
        Entry current = live(scope);
        if (current != null) {
            return Optional.of(current.record());
        }
        entries.put(scope, new Entry(marker, clock.instant().plus(ttl)));
        return Optional.empty();
    }

    @Override
    public synchronized void complete(String client, String key, IdempotencyRecord completed, Duration ttl) {
        entries.put(new Scope(client, key), new Entry(completed, clock.instant().plus(ttl)));
    }

    @Override
    public synchronized void release(String client, String key) {
        entries.remove(new Scope(client, key));
    }

    private Entry live(Scope scope) {
        Entry entry = entries.get(scope);
        if (entry != null && !clock.instant().isBefore(entry.expiresAt())) {
            entries.remove(scope);
            return null;
        }
        return entry;
    }
}
