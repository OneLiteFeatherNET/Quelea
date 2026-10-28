package net.onelitefeather.quelea.backend.support;

import net.onelitefeather.quelea.backend.party.port.PresenceStore;
import net.onelitefeather.quelea.backend.presence.PlayerPresence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Not thread-safe on purpose: the presence tests drive it from one thread. */
public final class InMemoryPresenceStore implements PresenceStore {

    private final Clock clock;
    private final Map<UUID, PlayerPresence> presence = new HashMap<>();
    private final Map<String, UUID> names = new HashMap<>();
    private final Map<String, Set<UUID>> proxyPlayers = new HashMap<>();
    private final Map<String, Instant> aliveUntil = new HashMap<>();
    private final Set<String> proxies = new HashSet<>();

    public InMemoryPresenceStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void put(PlayerPresence entry) {
        PlayerPresence previous = presence.get(entry.id());
        if (previous != null) {
            names.remove(key(previous.name()), entry.id());
            proxyPlayers.getOrDefault(previous.proxyId(), new HashSet<>()).remove(entry.id());
        }
        presence.put(entry.id(), entry);
        names.put(key(entry.name()), entry.id());
        proxyPlayers.computeIfAbsent(entry.proxyId(), id -> new HashSet<>()).add(entry.id());
        proxies.add(entry.proxyId());
    }

    @Override
    public Optional<PlayerPresence> find(UUID playerId) {
        return Optional.ofNullable(presence.get(playerId));
    }

    @Override
    public Optional<PlayerPresence> findByName(String name) {
        UUID id = names.get(key(name));
        return id == null ? Optional.empty() : find(id);
    }

    @Override
    public void remove(UUID playerId) {
        PlayerPresence previous = presence.remove(playerId);
        if (previous != null) {
            names.remove(key(previous.name()), playerId);
            proxyPlayers.getOrDefault(previous.proxyId(), new HashSet<>()).remove(playerId);
        }
    }

    @Override
    public Set<UUID> playersOf(String proxyId) {
        return Set.copyOf(proxyPlayers.getOrDefault(proxyId, Set.of()));
    }

    @Override
    public void touchProxy(String proxyId, Duration ttl) {
        aliveUntil.put(proxyId, clock.instant().plus(ttl));
        proxies.add(proxyId);
    }

    @Override
    public boolean isAlive(String proxyId) {
        Instant until = aliveUntil.get(proxyId);
        return until != null && until.isAfter(clock.instant());
    }

    @Override
    public Set<String> proxies() {
        return Set.copyOf(proxies);
    }

    @Override
    public long countOnlinePlayers() {
        return proxies.stream().mapToLong(proxyId -> proxyPlayers.getOrDefault(proxyId, Set.of()).size()).sum();
    }

    @Override
    public void forgetProxy(String proxyId) {
        proxies.remove(proxyId);
        aliveUntil.remove(proxyId);
        proxyPlayers.remove(proxyId);
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
