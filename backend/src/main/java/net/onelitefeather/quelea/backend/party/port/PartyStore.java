package net.onelitefeather.quelea.backend.party.port;

import net.onelitefeather.quelea.backend.party.Invite;
import net.onelitefeather.quelea.backend.party.Party;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartyStore {
    Optional<Party> find(UUID partyId);

    Optional<UUID> partyOf(UUID playerId);

    /** SET NX semantics: true if the player was free and is now bound to partyId. */
    boolean claimPlayer(UUID playerId, UUID partyId);

    /** Removes the binding only if it still points to partyId. */
    void releasePlayer(UUID playerId, UUID partyId);

    void save(Party party);

    void delete(UUID partyId);

    void saveInvite(Invite invite, Duration ttl);

    /** Must not return expired invites. */
    Optional<Invite> findInvite(UUID partyId, UUID targetId);

    /** Must not return expired invites. */
    List<Invite> invitesOf(UUID targetId);

    void deleteInvite(UUID partyId, UUID targetId);

    void deleteInvitesOf(UUID targetId);

    long countParties();

    /**
     * Marks a party that has one member: its expiry is due at {@code expireAt}; its warning is due at
     * {@code warnAt}, or never when {@code warnAt} is null.
     */
    void markAlone(UUID partyId, Instant expireAt, Instant warnAt);

    /** Removes the party from both lonely-party sets. */
    void clearAlone(UUID partyId);

    /** Parties whose warning is due at {@code now} or earlier. */
    List<UUID> dueWarnings(Instant now);

    /** Parties whose expiry is due at {@code now} or earlier. */
    List<UUID> dueExpiries(Instant now);

    void clearWarning(UUID partyId);
}
