package net.onelitefeather.quelea.backend.support;

import net.onelitefeather.quelea.backend.party.Invite;
import net.onelitefeather.quelea.backend.party.Party;
import net.onelitefeather.quelea.backend.party.port.PartyStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryPartyStore implements PartyStore {

    private record StoredInvite(Invite invite, Instant expiresAt) {
    }

    private record InviteKey(UUID partyId, UUID targetId) {
    }

    private final Clock clock;
    private final Map<UUID, Party> parties = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> bindings = new ConcurrentHashMap<>();
    private final Map<InviteKey, StoredInvite> invites = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> warnings = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> expiries = new ConcurrentHashMap<>();

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
