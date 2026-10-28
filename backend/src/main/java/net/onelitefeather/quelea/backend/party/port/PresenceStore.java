package net.onelitefeather.quelea.backend.party.port;

import net.onelitefeather.quelea.backend.presence.PlayerPresence;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PresenceStore {
    /** Also maintains the name index and the proxy membership. */
    void put(PlayerPresence presence);

    Optional<PlayerPresence> find(UUID playerId);

    /** Case-insensitive. */
    Optional<PlayerPresence> findByName(String name);

    void remove(UUID playerId);

    Set<UUID> playersOf(String proxyId);

    void touchProxy(String proxyId, Duration ttl);

    boolean isAlive(String proxyId);

    Set<String> proxies();

    /** Players online on all known proxies, summed over the per-proxy player sets. */
    long countOnlinePlayers();

    void forgetProxy(String proxyId);
}
