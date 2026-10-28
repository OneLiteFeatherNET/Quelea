package net.onelitefeather.quelea.backend.store.redis;

import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.micronaut.context.annotation.Requires;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import net.onelitefeather.quelea.backend.party.port.PresenceStore;
import net.onelitefeather.quelea.backend.presence.PlayerPresence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Network presence in Dragonfly: one JSON entry per player, a lower-cased name index, the set of players per proxy,
 * the set of known proxies and a heartbeat key per proxy that expires when the proxy goes silent.
 */
@Singleton
@Requires(beans = StatefulRedisConnection.class)
public class RedisPresenceStore implements PresenceStore {

    private final RedisCommands<String, String> commands;
    private final RedisKeys keys;
    private final ObjectMapper json;
    private final Clock clock;

    public RedisPresenceStore(StatefulRedisConnection<String, String> connection, RedisKeys keys,
                              ObjectMapper json, Clock clock) {
        this.commands = connection.sync();
        this.keys = keys;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public void put(PlayerPresence presence) {
        Optional<PlayerPresence> previous = find(presence.id());
        if (previous.isPresent()) {
            PlayerPresence old = previous.get();
            if (!keys.name(old.name()).equals(keys.name(presence.name()))) {
                RedisScripts.compareAndDelete(commands, keys.name(old.name()), presence.id().toString());
            }
            if (!old.proxyId().equals(presence.proxyId())) {
                commands.srem(keys.proxyPlayers(old.proxyId()), presence.id().toString());
            }
        }
        commands.set(keys.presence(presence.id()), write(presence));
        commands.set(keys.name(presence.name()), presence.id().toString());
        commands.sadd(keys.proxyPlayers(presence.proxyId()), presence.id().toString());
        commands.sadd(keys.proxies(), presence.proxyId());
    }

    @Override
    public Optional<PlayerPresence> find(UUID playerId) {
        return Optional.ofNullable(commands.get(keys.presence(playerId))).map(value -> read(value));
    }

    @Override
    public Optional<PlayerPresence> findByName(String name) {
        return Optional.ofNullable(commands.get(keys.name(name))).map(UUID::fromString).flatMap(this::find);
    }

    @Override
    public void remove(UUID playerId) {
        Optional<PlayerPresence> previous = find(playerId);
        if (previous.isEmpty()) {
            return;
        }
        PlayerPresence old = previous.get();
        commands.del(keys.presence(playerId));
        RedisScripts.compareAndDelete(commands, keys.name(old.name()), playerId.toString());
        commands.srem(keys.proxyPlayers(old.proxyId()), playerId.toString());
    }

    @Override
    public Set<UUID> playersOf(String proxyId) {
        return commands.smembers(keys.proxyPlayers(proxyId)).stream().map(UUID::fromString).collect(Collectors.toSet());
    }

    @Override
    public void touchProxy(String proxyId, Duration ttl) {
        commands.set(keys.proxyAlive(proxyId), clock.instant().toString(), SetArgs.Builder.px(ttl.toMillis()));
        commands.sadd(keys.proxies(), proxyId);
    }

    @Override
    public boolean isAlive(String proxyId) {
        return commands.exists(keys.proxyAlive(proxyId)) > 0;
    }

    @Override
    public Set<String> proxies() {
        return commands.smembers(keys.proxies());
    }

    @Override
    public long countOnlinePlayers() {
        long total = 0;
        for (String proxyId : proxies()) {
            Long online = commands.scard(keys.proxyPlayers(proxyId));
            total += online == null ? 0 : online;
        }
        return total;
    }

    @Override
    public void forgetProxy(String proxyId) {
        commands.del(keys.proxyPlayers(proxyId));
        commands.del(keys.proxyAlive(proxyId));
        commands.srem(keys.proxies(), proxyId);
    }

    private String write(PlayerPresence presence) {
        try {
            return json.writeValueAsString(presence);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot serialise presence", e);
        }
    }

    private PlayerPresence read(String value) {
        try {
            return json.readValue(value, PlayerPresence.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read presence", e);
        }
    }
}
