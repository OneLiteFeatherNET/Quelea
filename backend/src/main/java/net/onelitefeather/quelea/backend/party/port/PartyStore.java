package net.onelitefeather.quelea.backend.party.port;

import net.onelitefeather.quelea.backend.party.Invite;
import net.onelitefeather.quelea.backend.party.JoinRequest;
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
     * {@code warnAt}, or never when {@code warnAt} is null; a null {@code warnAt} also removes a warning set earlier.
     */
    void markAlone(UUID partyId, Instant expireAt, Instant warnAt);

    /** Removes the party from both lonely-party sets. */
    void clearAlone(UUID partyId);

    /** Parties whose warning is due at {@code now} or earlier. */
    List<UUID> dueWarnings(Instant now);

    /** Parties whose expiry is due at {@code now} or earlier. */
    List<UUID> dueExpiries(Instant now);

    void clearWarning(UUID partyId);

    /** Stores an open join request; it expires with {@code ttl}. */
    void saveJoinRequest(JoinRequest request, Duration ttl);

    /** Must not return expired requests. */
    Optional<JoinRequest> findJoinRequest(UUID partyId, UUID requesterId);

    void deleteJoinRequest(UUID partyId, UUID requesterId);

    /** Starts a cooldown of {@code ttl} for the player's requests to the party. */
    void setCooldown(UUID partyId, UUID requesterId, Duration ttl);

    /** The time left in the player's cooldown for the party; zero when there is none. */
    Duration cooldownLeft(UUID partyId, UUID requesterId);

    void clearCooldown(UUID partyId, UUID requesterId);

    /** Removes every open request and cooldown of the party; called when the party is disbanded. */
    void deleteRequestsOf(UUID partyId);

    void ban(UUID partyId, UUID playerId);

    void unban(UUID partyId, UUID playerId);

    boolean isBanned(UUID partyId, UUID playerId);

    /** Removes the ban set of the party; called when the party is disbanded. */
    void deleteBans(UUID partyId);
}
