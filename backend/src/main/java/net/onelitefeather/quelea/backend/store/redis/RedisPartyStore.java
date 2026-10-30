package net.onelitefeather.quelea.backend.store.redis;

import io.lettuce.core.Range;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.micronaut.context.annotation.Requires;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import net.onelitefeather.quelea.backend.party.Invite;
import net.onelitefeather.quelea.backend.party.Party;
import net.onelitefeather.quelea.backend.party.port.PartyStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Parties, player bindings and invites in Dragonfly. Parties and invites are JSON strings; invites expire through
 * their key TTL, so expired invites are never returned. The invite set per player only indexes the keys and may
 * hold stale party ids, which are removed when read.
 */
@Singleton
@Requires(beans = StatefulRedisConnection.class)
public class RedisPartyStore implements PartyStore {

    private final RedisCommands<String, String> commands;
    private final RedisKeys keys;
    private final ObjectMapper json;

    public RedisPartyStore(StatefulRedisConnection<String, String> connection, RedisKeys keys, ObjectMapper json) {
        this.commands = connection.sync();
        this.keys = keys;
        this.json = json;
    }

    @Override
    public Optional<Party> find(UUID partyId) {
        return Optional.ofNullable(commands.get(keys.party(partyId))).map(value -> read(value, Party.class));
    }

    @Override
    public Optional<UUID> partyOf(UUID playerId) {
        return Optional.ofNullable(commands.get(keys.playerParty(playerId))).map(UUID::fromString);
    }

    @Override
    public boolean claimPlayer(UUID playerId, UUID partyId) {
        return Boolean.TRUE.equals(commands.setnx(keys.playerParty(playerId), partyId.toString()));
    }

    @Override
    public void releasePlayer(UUID playerId, UUID partyId) {
        RedisScripts.compareAndDelete(commands, keys.playerParty(playerId), partyId.toString());
    }

    @Override
    public void save(Party party) {
        commands.set(keys.party(party.id()), write(party));
        commands.sadd(keys.parties(), party.id().toString());
    }

    @Override
    public void delete(UUID partyId) {
        commands.del(keys.party(partyId));
        commands.srem(keys.parties(), partyId.toString());
    }

    @Override
    public void saveInvite(Invite invite, Duration ttl) {
        commands.set(keys.invite(invite.partyId(), invite.targetId()), write(invite),
                SetArgs.Builder.px(ttl.toMillis()));
        commands.sadd(keys.playerInvites(invite.targetId()), invite.partyId().toString());
    }

    @Override
    public Optional<Invite> findInvite(UUID partyId, UUID targetId) {
        return Optional.ofNullable(commands.get(keys.invite(partyId, targetId))).map(value -> read(value, Invite.class));
    }

    @Override
    public List<Invite> invitesOf(UUID targetId) {
        String indexKey = keys.playerInvites(targetId);
        List<Invite> invites = new ArrayList<>();
        for (String partyId : commands.smembers(indexKey)) {
            String value = commands.get(keys.invite(UUID.fromString(partyId), targetId));
            if (value == null) {
                // The invite expired: the index entry is stale.
                commands.srem(indexKey, partyId);
            } else {
                invites.add(read(value, Invite.class));
            }
        }
        return invites;
    }

    @Override
    public void deleteInvite(UUID partyId, UUID targetId) {
        commands.del(keys.invite(partyId, targetId));
        commands.srem(keys.playerInvites(targetId), partyId.toString());
    }

    @Override
    public void deleteInvitesOf(UUID targetId) {
        String indexKey = keys.playerInvites(targetId);
        for (String partyId : commands.smembers(indexKey)) {
            commands.del(keys.invite(UUID.fromString(partyId), targetId));
        }
        commands.del(indexKey);
    }

    @Override
    public long countParties() {
        return commands.scard(keys.parties());
    }

    @Override
    public void markAlone(UUID partyId, Instant expireAt, Instant warnAt) {
        commands.zadd(keys.aloneExpire(), (double) expireAt.toEpochMilli(), partyId.toString());
        if (warnAt != null) {
            commands.zadd(keys.aloneWarn(), (double) warnAt.toEpochMilli(), partyId.toString());
        } else {
            // A restarted alone period without warning must not keep the warning of the one before.
            commands.zrem(keys.aloneWarn(), partyId.toString());
        }
    }

    @Override
    public void clearAlone(UUID partyId) {
        commands.zrem(keys.aloneWarn(), partyId.toString());
        commands.zrem(keys.aloneExpire(), partyId.toString());
    }

    @Override
    public List<UUID> dueWarnings(Instant now) {
        return due(keys.aloneWarn(), now);
    }

    @Override
    public List<UUID> dueExpiries(Instant now) {
        return due(keys.aloneExpire(), now);
    }

    @Override
    public void clearWarning(UUID partyId) {
        commands.zrem(keys.aloneWarn(), partyId.toString());
    }

    private List<UUID> due(String sortedSet, Instant now) {
        List<UUID> partyIds = new ArrayList<>();
        for (String partyId : commands.zrangebyscore(sortedSet, Range.create(0L, now.toEpochMilli()))) {
            partyIds.add(UUID.fromString(partyId));
        }
        return partyIds;
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot serialise " + value.getClass().getSimpleName(), e);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + type.getSimpleName(), e);
        }
    }
}
