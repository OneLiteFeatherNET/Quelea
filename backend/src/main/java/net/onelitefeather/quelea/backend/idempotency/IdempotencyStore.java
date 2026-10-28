package net.onelitefeather.quelea.backend.idempotency;

import java.time.Duration;
import java.util.Optional;

/**
 * Records of {@code Idempotency-Key} headers, scoped by the authenticated client so that two clients never share a
 * key. Records expire by their time to live.
 */
public interface IdempotencyStore {

    /**
     * Stores {@code marker} under the client and key if the key is free, for {@code ttl}. Returns empty when the marker
     * was stored; otherwise returns the record that already holds the key.
     */
    Optional<IdempotencyRecord> claim(String client, String key, IdempotencyRecord marker, Duration ttl);

    /** Replaces the key's record with {@code completed} for {@code ttl}. */
    void complete(String client, String key, IdempotencyRecord completed, Duration ttl);

    /** Drops the key's record, so the client may send the request again. */
    void release(String client, String key);
}
