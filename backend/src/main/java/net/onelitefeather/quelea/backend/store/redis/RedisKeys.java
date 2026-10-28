package net.onelitefeather.quelea.backend.store.redis;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.util.Locale;
import java.util.UUID;

/**
 * Builds every Dragonfly key the service writes. All keys share the configured prefix so that several
 * environments can use one Dragonfly. The layout is documented in design decision 2 of the party change.
 */
@Singleton
public class RedisKeys {

    private final String prefix;

    public RedisKeys(@Value("${quelea.redis.prefix:quelea:}") String prefix) {
        this.prefix = prefix;
    }

    /** Party state as JSON. */
    public String party(UUID partyId) {
        return prefix + "party:" + partyId;
    }

    /** Set of all party ids; its cardinality is the number of parties. */
    public String parties() {
        return prefix + "parties";
    }

    /** The party a player is bound to (a single party id). */
    public String playerParty(UUID playerId) {
        return prefix + "player:" + playerId + ":party";
    }

    /** One invite as JSON, expiring with the invite TTL. */
    public String invite(UUID partyId, UUID playerId) {
        return prefix + "invite:" + partyId + ":" + playerId;
    }

    /** Party ids the player holds invites for; entries may be stale and are dropped on read. */
    public String playerInvites(UUID playerId) {
        return prefix + "player:" + playerId + ":invites";
    }

    /** A player's presence as JSON. */
    public String presence(UUID playerId) {
        return prefix + "presence:" + playerId;
    }

    /** Name index: lower-cased player name to player id. */
    public String name(String name) {
        return prefix + "name:" + name.toLowerCase(Locale.ROOT);
    }

    /** Set of player ids currently on a proxy. */
    public String proxyPlayers(String proxyId) {
        return prefix + "proxy:" + proxyId + ":players";
    }

    /** Heartbeat of a proxy; the key expires when the proxy stops sending heartbeats. */
    public String proxyAlive(String proxyId) {
        return prefix + "proxy:" + proxyId + ":alive";
    }

    /** Set of all known proxy ids. */
    public String proxies() {
        return prefix + "proxies";
    }

    /** Sorted set of lonely parties (one member) scored by the epoch millis of their warning. */
    public String aloneWarn() {
        return prefix + "parties:alone:warn";
    }

    /** Sorted set of lonely parties (one member) scored by the epoch millis of their expiry. */
    public String aloneExpire() {
        return prefix + "parties:alone:expire";
    }

    /** Idempotency record of one client's key; {@code keyHash} is the SHA-256 of the key, never the key itself. */
    public String idempotency(String client, String keyHash) {
        return prefix + "idem:" + client + ":" + keyHash;
    }

    /** A distributed lock; {@code name} is the lock name as passed to {@code LockService}. */
    public String lock(String name) {
        return prefix + "lock:" + name;
    }
}
