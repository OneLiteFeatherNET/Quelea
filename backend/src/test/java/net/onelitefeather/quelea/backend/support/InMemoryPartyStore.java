package net.onelitefeather.quelea.backend.support;

import net.onelitefeather.quelea.backend.party.Invite;
import net.onelitefeather.quelea.backend.party.JoinRequest;
import net.onelitefeather.quelea.backend.party.Party;
import net.onelitefeather.quelea.backend.party.port.PartyStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryPartyStore implements PartyStore {

    private record StoredInvite(Invite invite, Instant expiresAt) {
    }

    private record InviteKey(UUID partyId, UUID targetId) {
    }

    /** A party and a player: the key of a request and of a cooldown. */
    private record RequestKey(UUID partyId, UUID playerId) {
    }

    private record Expiring<T>(T value, Instant expiresAt) {
    }

    private final Clock clock;
    private final Map<UUID, Party> parties = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> bindings = new ConcurrentHashMap<>();
    private final Map<InviteKey, StoredInvite> invites = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> warnings = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> expiries = new ConcurrentHashMap<>();
    private final Map<RequestKey, Expiring<JoinRequest>> joinRequests = new ConcurrentHashMap<>();
    private final Map<RequestKey, Instant> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> bans = new ConcurrentHashMap<>();

    public InMemoryPartyStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Optional<Party> find(UUID partyId) {
        return Optional.ofNullable(parties.get(partyId));
    }

    @Override
    public Optional<UUID> partyOf(UUID playerId) {
        return Optional.ofNullable(bindings.get(playerId));
    }

    @Override
    public boolean claimPlayer(UUID playerId, UUID partyId) {
        return bindings.putIfAbsent(playerId, partyId) == null;
    }

    @Override
    public void releasePlayer(UUID playerId, UUID partyId) {
        bindings.remove(playerId, partyId);
    }

    @Override
    public void save(Party party) {
        parties.put(party.id(), party);
    }

    @Override
    public void delete(UUID partyId) {
        parties.remove(partyId);
    }

    @Override
    public void saveInvite(Invite invite, Duration ttl) {
        invites.put(new InviteKey(invite.partyId(), invite.targetId()),
                new StoredInvite(invite, clock.instant().plus(ttl)));
    }

    @Override
    public Optional<Invite> findInvite(UUID partyId, UUID targetId) {
        return Optional.ofNullable(invites.get(new InviteKey(partyId, targetId)))
                .filter(this::isLive)
                .map(StoredInvite::invite);
    }

    @Override
    public List<Invite> invitesOf(UUID targetId) {
        return invites.entrySet().stream()
                .filter(entry -> entry.getKey().targetId().equals(targetId))
                .map(Map.Entry::getValue)
                .filter(this::isLive)
                .map(StoredInvite::invite)
                .toList();
    }

    @Override
    public void deleteInvite(UUID partyId, UUID targetId) {
        invites.remove(new InviteKey(partyId, targetId));
    }

    @Override
    public void deleteInvitesOf(UUID targetId) {
        invites.keySet().removeIf(key -> key.targetId().equals(targetId));
    }

    @Override
    public long countParties() {
        return parties.size();
    }

    @Override
    public void markAlone(UUID partyId, Instant expireAt, Instant warnAt) {
        expiries.put(partyId, expireAt);
        if (warnAt != null) {
            warnings.put(partyId, warnAt);
        } else {
            warnings.remove(partyId);
        }
    }

    @Override
    public void clearAlone(UUID partyId) {
        warnings.remove(partyId);
        expiries.remove(partyId);
    }

    @Override
    public List<UUID> dueWarnings(Instant now) {
        return due(warnings, now);
    }

    @Override
    public List<UUID> dueExpiries(Instant now) {
        return due(expiries, now);
    }

    @Override
    public void clearWarning(UUID partyId) {
        warnings.remove(partyId);
    }

    @Override
    public void saveJoinRequest(JoinRequest request, Duration ttl) {
        joinRequests.put(new RequestKey(request.partyId(), request.requesterId()),
                new Expiring<>(request, clock.instant().plus(ttl)));
    }

    @Override
    public Optional<JoinRequest> findJoinRequest(UUID partyId, UUID requesterId) {
        return Optional.ofNullable(joinRequests.get(new RequestKey(partyId, requesterId)))
                .filter(stored -> isLive(stored.expiresAt()))
                .map(Expiring::value);
    }

    @Override
    public void deleteJoinRequest(UUID partyId, UUID requesterId) {
        joinRequests.remove(new RequestKey(partyId, requesterId));
    }

    @Override
    public void setCooldown(UUID partyId, UUID requesterId, Duration ttl) {
        cooldowns.put(new RequestKey(partyId, requesterId), clock.instant().plus(ttl));
    }

    @Override
    public Duration cooldownLeft(UUID partyId, UUID requesterId) {
        Instant until = cooldowns.get(new RequestKey(partyId, requesterId));
        if (until == null || !clock.instant().isBefore(until)) {
            return Duration.ZERO;
        }
        return Duration.between(clock.instant(), until);
    }

    @Override
    public void clearCooldown(UUID partyId, UUID requesterId) {
        cooldowns.remove(new RequestKey(partyId, requesterId));
    }

    @Override
    public void deleteRequestsOf(UUID partyId) {
        joinRequests.keySet().removeIf(key -> key.partyId().equals(partyId));
        cooldowns.keySet().removeIf(key -> key.partyId().equals(partyId));
    }

    @Override
    public void ban(UUID partyId, UUID playerId) {
        bans.computeIfAbsent(partyId, id -> ConcurrentHashMap.newKeySet()).add(playerId);
    }

    @Override
    public void unban(UUID partyId, UUID playerId) {
        Set<UUID> set = bans.get(partyId);
        if (set != null) {
            set.remove(playerId);
        }
    }

    @Override
    public boolean isBanned(UUID partyId, UUID playerId) {
        Set<UUID> set = bans.get(partyId);
        return set != null && set.contains(playerId);
    }

    @Override
    public void deleteBans(UUID partyId) {
        bans.remove(partyId);
    }

    private boolean isLive(Instant expiresAt) {
        return clock.instant().isBefore(expiresAt);
    }

    private static List<UUID> due(Map<UUID, Instant> entries, Instant now) {
        return entries.entrySet().stream()
                .filter(entry -> !entry.getValue().isAfter(now))
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();
    }

    private boolean isLive(StoredInvite stored) {
        return clock.instant().isBefore(stored.expiresAt());
    }
}
