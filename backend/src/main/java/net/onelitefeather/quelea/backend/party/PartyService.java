package net.onelitefeather.quelea.backend.party;

import jakarta.inject.Singleton;
import net.onelitefeather.quelea.backend.event.PartyEvent;
import net.onelitefeather.quelea.backend.event.PartyEventType;
import net.onelitefeather.quelea.backend.event.TraceContext;
import net.onelitefeather.quelea.backend.party.port.EventPublisher;
import net.onelitefeather.quelea.backend.party.port.LockService;
import net.onelitefeather.quelea.backend.party.port.PartyStore;
import net.onelitefeather.quelea.backend.party.port.PresenceStore;
import net.onelitefeather.quelea.backend.presence.PlayerPresence;
import net.onelitefeather.quelea.backend.problem.PartyException;
import net.onelitefeather.quelea.backend.problem.PartyProblem;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Party rules. Every mutation of an existing party runs under the lock {@code party:<partyId>}; reads do not
 * lock. Joining claims the player's binding in the store, so a player is in at most one party even when two
 * accepts race on different parties.
 */
@Singleton
public class PartyService {

    /** Disband reasons carried by party.disbanded. */
    public static final String REASON_LEADER = "leader";
    public static final String REASON_LAST_MEMBER_LEFT = "last-member-left";
    public static final String REASON_EXPIRED_ALONE = "expired-alone";

    private final PartyStore parties;
    private final PresenceStore presence;
    private final LockService locks;
    private final EventPublisher events;
    private final PartySettings settings;
    private final Clock clock;

    public PartyService(PartyStore parties, PresenceStore presence, LockService locks, EventPublisher events,
                        PartySettings settings, Clock clock) {
        this.parties = parties;
        this.presence = presence;
        this.locks = locks;
        this.events = events;
        this.settings = settings;
        this.clock = clock;
    }

    public Party create(UUID leaderId) {
        PlayerPresence leader = onlinePlayer(leaderId);
        if (parties.partyOf(leaderId).isPresent()) {
            throw new PartyException(PartyProblem.ALREADY_IN_PARTY, "The player is already in a party.");
        }
        UUID partyId = UUID.randomUUID();
        if (!parties.claimPlayer(leaderId, partyId)) {
            throw new PartyException(PartyProblem.ALREADY_IN_PARTY, "The player is already in a party.");
        }
        Instant now = clock.instant();
        Party party = new Party(partyId, leaderId, List.of(new Member(leaderId, leader.name(), now)), now, 1);
        parties.save(party);
        markAlone(party);
        publish(PartyEventType.CREATED, party, Map.of());
        return party;
    }

    public Party get(UUID partyId) {
        return require(partyId);
    }

    public Party partyOf(UUID playerId) {
        UUID partyId = parties.partyOf(playerId)
                .orElseThrow(() -> new PartyException(PartyProblem.NOT_IN_PARTY, "The player is not in a party."));
        return parties.find(partyId)
                .orElseThrow(() -> new PartyException(PartyProblem.NOT_IN_PARTY, "The player is not in a party."));
    }

    /** Invites {@code targetName}, creating the inviter's party first when the inviter has none. */
    public Invite invite(UUID actorId, String targetName) {
        PlayerPresence actor = onlinePlayer(actorId);
        PlayerPresence target = presence.findByName(targetName)
                .orElseThrow(() -> new PartyException(PartyProblem.PLAYER_NOT_ONLINE, "No online player is named " + targetName + "."));
        if (target.id().equals(actorId)) {
            throw new PartyException(PartyProblem.CANNOT_TARGET_SELF);
        }
        // Checked before the party is created, so a failed first invite does not leave a party of one behind.
        if (parties.partyOf(target.id()).isPresent()) {
            throw new PartyException(PartyProblem.ALREADY_IN_PARTY, target.name() + " is already in a party.");
        }
        UUID partyId = parties.partyOf(actorId).orElseGet(() -> create(actorId).id());
        return locked(partyId, () -> {
            Party party = require(partyId);
            if (!party.isLeader(actorId)) {
                throw new PartyException(PartyProblem.NOT_LEADER, "Only the party leader may invite players.");
            }
            if (parties.partyOf(target.id()).isPresent()) {
                throw new PartyException(PartyProblem.ALREADY_IN_PARTY, target.name() + " is already in a party.");
            }
            ensureRoom(party);
            Duration ttl = settings.getInviteTtl();
            Invite invite = new Invite(partyId, actorId, actor.name(), target.id(), target.name(), clock.instant().plus(ttl));
            parties.saveInvite(invite, ttl);
            publish(PartyEventType.INVITED, party, Map.of(
                    "inviterId", actorId,
                    "inviterName", actor.name(),
                    "targetId", target.id(),
                    "targetName", target.name(),
                    "expiresAt", invite.expiresAt()));
            return invite;
        });
    }

    public List<Invite> invitesOf(UUID playerId) {
        return parties.invitesOf(playerId);
    }

    public Party accept(UUID playerId, UUID partyId) {
        Invite invite = parties.findInvite(partyId, playerId)
                .orElseThrow(() -> new PartyException(PartyProblem.INVITE_NOT_FOUND, "There is no current invite to that party."));
        return locked(partyId, () -> {
            Party party = require(partyId);
            if (parties.partyOf(playerId).isPresent()) {
                throw new PartyException(PartyProblem.ALREADY_IN_PARTY, "The player is already in a party.");
            }
            ensureRoom(party);
            if (!parties.claimPlayer(playerId, partyId)) {
                throw new PartyException(PartyProblem.ALREADY_IN_PARTY, "The player is already in a party.");
            }
            List<Member> members = new ArrayList<>(party.members());
            members.add(new Member(playerId, invite.targetName(), clock.instant()));
            Party joined = party.withMembers(members);
            parties.save(joined);
            parties.clearAlone(partyId);
            parties.deleteInvite(partyId, playerId);
            publish(PartyEventType.JOINED, joined, Map.of("playerId", playerId, "playerName", invite.targetName()));
            return joined;
        });
    }

    public void deny(UUID playerId, UUID partyId) {
        Invite invite = parties.findInvite(partyId, playerId)
                .orElseThrow(() -> new PartyException(PartyProblem.INVITE_NOT_FOUND, "There is no current invite to that party."));
        parties.deleteInvite(partyId, playerId);
        Party party = parties.find(partyId).orElse(null);
        publish(PartyEventType.DENIED, partyId, party == null ? null : party.leaderId(),
                party == null ? List.of() : party.members(), party == null ? 0 : party.version(),
                Map.of("playerId", playerId, "playerName", invite.targetName()));
    }

    public void leave(UUID playerId, String reason) {
        UUID partyId = parties.partyOf(playerId)
                .orElseThrow(() -> new PartyException(PartyProblem.NOT_IN_PARTY, "The player is not in a party."));
        locked(partyId, () -> {
            Party party = parties.find(partyId).orElse(null);
            Member member = party == null ? null : party.member(playerId).orElse(null);
            if (member == null) {
                // The binding outlived the party or the membership: drop it and report the player as not in a party.
                parties.releasePlayer(playerId, partyId);
                throw new PartyException(PartyProblem.NOT_IN_PARTY, "The player is not in a party.");
            }
            removeMember(party, member, PartyEventType.LEFT,
                    Map.of("playerId", member.id(), "playerName", member.name(), "reason", reason));
            return null;
        });
    }

    public Party kick(UUID partyId, UUID actorId, UUID targetId) {
        return kick(partyId, actorId, targetId, Precondition.NONE);
    }

    public Party kick(UUID partyId, UUID actorId, UUID targetId, Precondition precondition) {
        return locked(partyId, () -> {
            Party party = require(partyId);
            precondition.check(party);
            requireLeader(party, actorId);
            if (actorId.equals(targetId)) {
                throw new PartyException(PartyProblem.CANNOT_TARGET_SELF, "The leader cannot kick themselves.");
            }
            Member target = party.member(targetId)
                    .orElseThrow(() -> new PartyException(PartyProblem.NOT_MEMBER, "The player is not a member of this party."));
            return removeMember(party, target, PartyEventType.KICKED,
                    Map.of("playerId", target.id(), "playerName", target.name()));
        });
    }

    public Party promote(UUID partyId, UUID actorId, UUID targetId) {
        return promote(partyId, actorId, targetId, Precondition.NONE);
    }

    public Party promote(UUID partyId, UUID actorId, UUID targetId, Precondition precondition) {
        return locked(partyId, () -> {
            Party party = require(partyId);
            precondition.check(party);
            requireLeader(party, actorId);
            if (actorId.equals(targetId)) {
                throw new PartyException(PartyProblem.CANNOT_TARGET_SELF, "The leader is already the leader.");
            }
            if (!party.isMember(targetId)) {
                throw new PartyException(PartyProblem.NOT_MEMBER, "The player is not a member of this party.");
            }
            Party promoted = party.withLeader(targetId);
            parties.save(promoted);
            publish(PartyEventType.LEADER_CHANGED, promoted, Map.of("previousLeaderId", actorId, "leaderId", targetId));
            return promoted;
        });
    }

    public void disband(UUID partyId, UUID actorId) {
        disband(partyId, actorId, Precondition.NONE);
    }

    public void disband(UUID partyId, UUID actorId, Precondition precondition) {
        locked(partyId, () -> {
            Party party = require(partyId);
            precondition.check(party);
            requireLeader(party, actorId);
            disbandParty(party, REASON_LEADER);
            return null;
        });
    }

    /**
     * Disbands a party that still has one member once its alone timeout has passed. Runs under the party lock, so a
     * join that landed in the meantime keeps the party. Clears the party from the lonely sets either way.
     */
    public void expire(UUID partyId) {
        locked(partyId, () -> {
            Party party = parties.find(partyId).orElse(null);
            if (party == null || party.members().size() != 1) {
                parties.clearAlone(partyId);
                return null;
            }
            disbandParty(party, REASON_EXPIRED_ALONE);
            return null;
        });
    }

    /** Publishes party.expiring once for a party that is still alone; its expiry time comes from the alone timeout. */
    public void warnExpiring(UUID partyId) {
        locked(partyId, () -> {
            parties.clearWarning(partyId);
            Party party = parties.find(partyId).orElse(null);
            if (party == null || party.members().size() != 1) {
                return null;
            }
            publish(PartyEventType.EXPIRING, party, Map.of("expiresAt", party.createdAt().plus(settings.getAloneTimeout())));
            return null;
        });
    }

    /** Leader only. The event carries the server name; followers are moved by the proxies. */
    public void warp(UUID partyId, UUID actorId, String server) {
        warp(partyId, actorId, server, Precondition.NONE);
    }

    /** Runs under the party lock like the other leader commands, so an {@code If-Match} is compared with the current version. */
    public void warp(UUID partyId, UUID actorId, String server, Precondition precondition) {
        locked(partyId, () -> {
            Party party = require(partyId);
            precondition.check(party);
            requireLeader(party, actorId);
            publish(PartyEventType.WARP, party, Map.of("server", server));
            return null;
        });
    }

    /** Member only. */
    public void chat(UUID partyId, UUID actorId, String message) {
        Party party = require(partyId);
        Member sender = party.member(actorId)
                .orElseThrow(() -> new PartyException(PartyProblem.NOT_MEMBER, "Only party members may chat."));
        publish(PartyEventType.CHAT, party, Map.of("senderId", actorId, "senderName", sender.name(), "message", message));
    }

    /**
     * Removes {@code gone} (after LEFT or KICKED), then either disbands a party of at most one member or hands
     * leadership to the longest-standing remaining member. Returns the party state after the change.
     */
    private Party removeMember(Party party, Member gone, PartyEventType type, Map<String, Object> payload) {
        List<Member> remaining = party.members().stream()
                .filter(member -> !member.id().equals(gone.id()))
                .toList();
        parties.releasePlayer(gone.id(), party.id());
        UUID leaderId = party.leaderId();
        if (party.isLeader(gone.id()) && !remaining.isEmpty()) {
            leaderId = remaining.stream()
                    .min(Comparator.comparing(Member::joinedAt))
                    .map(Member::id)
                    .orElseThrow();
        }
        Party after = new Party(party.id(), leaderId, remaining, party.createdAt(), party.version() + 1);
        if (remaining.size() <= 1) {
            // Two changes in a row: the departure, then the disband. Each event carries its own version.
            publish(type, party.id(), leaderId, remaining, party.version() + 1, payload);
            disbandParty(party, REASON_LAST_MEMBER_LEFT, remaining, party.version() + 2);
            return after;
        }
        parties.save(after);
        publish(type, after, payload);
        if (!leaderId.equals(party.leaderId())) {
            publish(PartyEventType.LEADER_CHANGED, after,
                    Map.of("previousLeaderId", gone.id(), "leaderId", leaderId));
        }
        return after;
    }

    private void disbandParty(Party party, String reason) {
        disbandParty(party, reason, party.members(), party.version() + 1);
    }

    /**
     * Releases every bound member, deletes the party and publishes DISBANDED with the former members. The party is gone
     * afterwards, so the event carries the version the disband would have had.
     */
    private void disbandParty(Party party, String reason, List<Member> stillBound, long version) {
        for (Member member : stillBound) {
            parties.releasePlayer(member.id(), party.id());
        }
        parties.clearAlone(party.id());
        parties.delete(party.id());
        List<Map<String, Object>> formerMembers = new ArrayList<>();
        for (Member member : party.members()) {
            formerMembers.add(Map.of("id", member.id(), "name", member.name()));
        }
        publish(PartyEventType.DISBANDED, party.id(), null, List.of(), version,
                Map.of("formerMembers", formerMembers, "reason", reason));
    }

    /** A party of one member expires after the alone timeout and is warned before, unless the warning is off. */
    private void markAlone(Party party) {
        Instant expireAt = party.createdAt().plus(settings.getAloneTimeout());
        Duration warning = settings.getAloneWarning();
        Instant warnAt = warning.isZero() || warning.isNegative() ? null : expireAt.minus(warning);
        parties.markAlone(party.id(), expireAt, warnAt);
    }

    private <T> T locked(UUID partyId, Supplier<T> action) {
        return locks.withLock("party:" + partyId, action);
    }

    private Party require(UUID partyId) {
        return parties.find(partyId)
                .orElseThrow(() -> new PartyException(PartyProblem.PARTY_NOT_FOUND, "The party does not exist."));
    }

    private PlayerPresence onlinePlayer(UUID playerId) {
        return presence.find(playerId)
                .orElseThrow(() -> new PartyException(PartyProblem.PLAYER_NOT_ONLINE, "The player is not online."));
    }

    private void requireLeader(Party party, UUID actorId) {
        if (!party.isLeader(actorId)) {
            throw new PartyException(PartyProblem.NOT_LEADER, "Only the party leader may do this.");
        }
    }

    private void ensureRoom(Party party) {
        if (party.members().size() >= settings.getMaxSize()) {
            throw new PartyException(PartyProblem.PARTY_FULL, "The party is full.");
        }
    }

    private void publish(PartyEventType type, Party party, Map<String, Object> payload) {
        publish(type, party.id(), party.leaderId(), party.members(), party.version(), payload);
    }

    private void publish(PartyEventType type, UUID partyId, UUID leaderId, List<Member> members, long version,
                         Map<String, Object> payload) {
        events.publish(new PartyEvent(
                UUID.randomUUID(),
                type.wireName(),
                partyId,
                clock.instant(),
                leaderId,
                List.copyOf(members),
                Collections.unmodifiableMap(new LinkedHashMap<>(payload)),
                TraceContext.traceparent(),
                version));
    }
}
