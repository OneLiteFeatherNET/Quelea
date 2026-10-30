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
    private final PartyStatistics statistics;

    public PartyService(PartyStore parties, PresenceStore presence, LockService locks, EventPublisher events,
                        PartySettings settings, Clock clock, PartyStatistics statistics) {
        this.parties = parties;
        this.presence = presence;
        this.locks = locks;
        this.events = events;
        this.settings = settings;
        this.clock = clock;
        this.statistics = statistics;
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
        Party party = new Party(partyId, leaderId, List.of(new Member(leaderId, leader.name(), now)), now, 1, now);
        parties.save(party);
        markAlone(party);
        statistics.created();
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
            // The leader's explicit invite lifts a kick ban, at the moment the invite is created.
            parties.unban(partyId, target.id());
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
            statistics.joined(joined.members().size());
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

    /**
     * A player asks to join the party of {@code targetName}. A public party admits the player at once; a private one
     * records a request that expires after its TTL, and the leader answers it. A denial or an expiry starts a cooldown.
     */
    public JoinOutcome requestJoin(UUID requesterId, String targetName) {
        PlayerPresence requester = onlinePlayer(requesterId);
        requireNotInParty(requesterId);
        UUID partyId = targetParty(requesterId, targetName);
        return locked(partyId, () -> {
            Party party = require(partyId);
            requireNotInParty(requesterId);
            if (parties.isBanned(partyId, requesterId)) {
                throw new PartyException(PartyProblem.PARTY_BANNED, "The player was removed from this party.");
            }
            if (party.publicParty()) {
                return new JoinOutcome.Joined(admit(party, requesterId, requester.name()));
            }
            // The pending check comes first: the cooldown key also exists while the request is open.
            if (parties.findJoinRequest(partyId, requesterId).isPresent()) {
                throw new PartyException(PartyProblem.JOIN_REQUEST_PENDING, "A join request to this party is already open.");
            }
            Duration cooldown = parties.cooldownLeft(partyId, requesterId);
            if (!cooldown.isZero()) {
                throw new PartyException(PartyProblem.JOIN_REQUEST_COOLDOWN,
                        "The player may ask this party again after the cooldown.", wholeSeconds(cooldown));
            }
            ensureRoom(party);
            Duration ttl = settings.getJoinRequestTtl();
            Instant expiresAt = clock.instant().plus(ttl);
            JoinRequest request = new JoinRequest(partyId, requesterId, requester.name(), expiresAt);
            parties.saveJoinRequest(request, ttl);
            // The cooldown outlives the request: while it is pending it is the duplicate check, after an expiry it holds
            // the player back until exactly the cooldown has passed since the expiry.
            parties.setCooldown(partyId, requesterId, ttl.plus(settings.getJoinRequestCooldown()));
            publish(PartyEventType.JOIN_REQUESTED, party, Map.of(
                    "requesterId", requesterId,
                    "requesterName", requester.name(),
                    "expiresAt", expiresAt));
            return new JoinOutcome.Requested(request);
        });
    }

    /** Joins {@code targetName}'s party directly. Only a public party admits a player without a request. */
    public Party join(UUID playerId, String targetName) {
        PlayerPresence joiner = onlinePlayer(playerId);
        requireNotInParty(playerId);
        UUID partyId = targetParty(playerId, targetName);
        return locked(partyId, () -> {
            Party party = require(partyId);
            if (!party.publicParty()) {
                throw new PartyException(PartyProblem.PARTY_NOT_PUBLIC, "The party is not public; ask to join with /party request.");
            }
            requireNotInParty(playerId);
            if (parties.isBanned(partyId, playerId)) {
                throw new PartyException(PartyProblem.PARTY_BANNED, "The player was removed from this party.");
            }
            return admit(party, playerId, joiner.name());
        });
    }

    /** Leader only. Accepts the open request of {@code requesterName}, which joins them like an invite does. */
    public Party acceptRequest(UUID partyId, UUID actorId, String requesterName) {
        return acceptRequest(partyId, actorId, requesterName, Precondition.NONE);
    }

    public Party acceptRequest(UUID partyId, UUID actorId, String requesterName, Precondition precondition) {
        return locked(partyId, () -> {
            Party party = require(partyId);
            precondition.check(party);
            requireLeader(party, actorId);
            UUID requesterId = presenceOfName(requesterName).id();
            JoinRequest request = parties.findJoinRequest(partyId, requesterId)
                    .orElseThrow(() -> new PartyException(PartyProblem.JOIN_REQUEST_NOT_FOUND, "There is no open request from that player."));
            requireNotInParty(requesterId);
            if (parties.isBanned(partyId, requesterId)) {
                throw new PartyException(PartyProblem.PARTY_BANNED, "The player was removed from this party.");
            }
            Party joined = admit(party, requesterId, request.requesterName());
            parties.deleteJoinRequest(partyId, requesterId);
            parties.clearCooldown(partyId, requesterId);
            publish(PartyEventType.JOIN_REQUEST_ANSWERED, joined, Map.of(
                    "requesterId", requesterId,
                    "requesterName", request.requesterName(),
                    "outcome", "accepted"));
            return joined;
        });
    }

    /** Leader only. Denies the open request of {@code requesterName} and starts the requester's cooldown. */
    public void rejectRequest(UUID partyId, UUID actorId, String requesterName) {
        rejectRequest(partyId, actorId, requesterName, Precondition.NONE);
    }

    public void rejectRequest(UUID partyId, UUID actorId, String requesterName, Precondition precondition) {
        locked(partyId, () -> {
            Party party = require(partyId);
            precondition.check(party);
            requireLeader(party, actorId);
            UUID requesterId = presenceOfName(requesterName).id();
            JoinRequest request = parties.findJoinRequest(partyId, requesterId)
                    .orElseThrow(() -> new PartyException(PartyProblem.JOIN_REQUEST_NOT_FOUND, "There is no open request from that player."));
            parties.deleteJoinRequest(partyId, requesterId);
            parties.setCooldown(partyId, requesterId, settings.getJoinRequestCooldown());
            publish(PartyEventType.JOIN_REQUEST_ANSWERED, party, Map.of(
                    "requesterId", requesterId,
                    "requesterName", request.requesterName(),
                    "outcome", "denied"));
            return null;
        });
    }

    /**
     * Leader only. Opens or closes the party. Closing needs no permission; the proxy checks it for opening only. A change
     * that does not alter the flag changes nothing and keeps the version.
     */
    public Party setPublic(UUID partyId, UUID actorId, boolean open, Precondition precondition) {
        return locked(partyId, () -> {
            Party party = require(partyId);
            precondition.check(party);
            requireLeader(party, actorId);
            if (party.publicParty() == open) {
                return party;
            }
            Party changed = party.withPublic(open);
            parties.save(changed);
            return changed;
        });
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
            Party after = removeMember(party, target, PartyEventType.KICKED,
                    Map.of("playerId", target.id(), "playerName", target.name()));
            // Ban the kicked player in the same locked command; the ban ends with the party.
            parties.ban(partyId, target.id());
            return after;
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
            publish(PartyEventType.LEADER_CHANGED, promoted,
                    leaderChangedPayload(actorId, targetId, party.publicParty()));
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
            publish(PartyEventType.EXPIRING, party, Map.of("expiresAt", party.aloneStart().plus(settings.getAloneTimeout())));
            return null;
        });
    }

    /** Leader only. The event carries the server name; followers are moved by the proxies. */
    public void warp(UUID partyId, UUID actorId, String server) {
        warp(partyId, actorId, server, Precondition.NONE);
    }

    /** Runs under the party lock like the other leader commands, so an {@code If-Match} is compared with the current version. */
    public void warp(UUID partyId, UUID actorId, String server, Precondition precondition) {
        warp(partyId, actorId, server, null, precondition);
    }

    /** {@code fromServer} is the server the leader left; it is carried as {@code from} when present. */
    public void warp(UUID partyId, UUID actorId, String server, String fromServer, Precondition precondition) {
        locked(partyId, () -> {
            Party party = require(partyId);
            precondition.check(party);
            requireLeader(party, actorId);
            Map<String, Object> payload = fromServer == null
                    ? Map.of("server", server)
                    : Map.of("server", server, "from", fromServer);
            publish(PartyEventType.WARP, party, payload);
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
     * Removes {@code gone} (after LEFT or KICKED). A party left with no member is disbanded; a party left with one
     * member stays open and starts a new alone period at this moment; a larger party keeps its members. Leadership
     * passes to the longest-standing remaining member when the leader left. Returns the party state after the change.
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
        if (remaining.isEmpty()) {
            // Two changes in a row: the departure, then the disband. Each event carries its own version.
            Party empty = new Party(party.id(), leaderId, remaining, party.createdAt(), party.version() + 1, null);
            publish(type, party.id(), leaderId, remaining, party.version() + 1, payload);
            disbandParty(party, REASON_LAST_MEMBER_LEFT, remaining, party.version() + 2);
            return empty;
        }
        Instant aloneSince = remaining.size() == 1 ? clock.instant() : null;
        // A change of leader closes the party (D2); a non-leader departure keeps it open (D10).
        boolean stillPublic = party.publicParty() && leaderId.equals(party.leaderId());
        Party after = new Party(party.id(), leaderId, remaining, party.createdAt(), party.version() + 1, aloneSince, stillPublic);
        parties.save(after);
        if (aloneSince != null) {
            markAlone(after);
        }
        publish(type, after, payload);
        if (!leaderId.equals(party.leaderId())) {
            publish(PartyEventType.LEADER_CHANGED, after,
                    leaderChangedPayload(gone.id(), leaderId, party.publicParty()));
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
        // Open requests, cooldowns and kick bans belong to the party and go with it.
        parties.deleteRequestsOf(party.id());
        parties.deleteBans(party.id());
        parties.delete(party.id());
        statistics.disbanded(reason, Duration.between(party.createdAt(), clock.instant()));
        List<Map<String, Object>> formerMembers = new ArrayList<>();
        for (Member member : party.members()) {
            formerMembers.add(Map.of("id", member.id(), "name", member.name()));
        }
        publish(PartyEventType.DISBANDED, party.id(), null, List.of(), version,
                Map.of("formerMembers", formerMembers, "reason", reason));
    }

    /**
     * A party of one member expires after the alone timeout, counted from the start of its alone period, and is warned
     * before, unless the warning is off.
     */
    private void markAlone(Party party) {
        Instant expireAt = party.aloneStart().plus(settings.getAloneTimeout());
        Duration warning = settings.getAloneWarning();
        Instant warnAt = warning.isZero() || warning.isNegative() ? null : expireAt.minus(warning);
        parties.markAlone(party.id(), expireAt, warnAt);
    }

    /** Adds a player to a party under its lock: the room check, the player's binding, the new version and the event. */
    private Party admit(Party party, UUID playerId, String name) {
        ensureRoom(party);
        if (!parties.claimPlayer(playerId, party.id())) {
            throw new PartyException(PartyProblem.ALREADY_IN_PARTY, "The player is already in a party.");
        }
        List<Member> members = new ArrayList<>(party.members());
        members.add(new Member(playerId, name, clock.instant()));
        Party joined = party.withMembers(members);
        parties.save(joined);
        parties.clearAlone(party.id());
        statistics.joined(joined.members().size());
        publish(PartyEventType.JOINED, joined, Map.of("playerId", playerId, "playerName", name));
        return joined;
    }

    /** Resolves the target by name and returns the party the target is in. The requester must not be the target. */
    private UUID targetParty(UUID requesterId, String targetName) {
        PlayerPresence target = presenceOfName(targetName);
        if (target.id().equals(requesterId)) {
            throw new PartyException(PartyProblem.CANNOT_TARGET_SELF);
        }
        return parties.partyOf(target.id())
                .orElseThrow(() -> new PartyException(PartyProblem.TARGET_NOT_IN_PARTY, target.name() + " is not in a party."));
    }

    private PlayerPresence presenceOfName(String name) {
        return presence.findByName(name)
                .orElseThrow(() -> new PartyException(PartyProblem.PLAYER_NOT_ONLINE, "No online player is named " + name + "."));
    }

    private void requireNotInParty(UUID playerId) {
        if (parties.partyOf(playerId).isPresent()) {
            throw new PartyException(PartyProblem.ALREADY_IN_PARTY, "The player is already in a party.");
        }
    }

    /**
     * The payload of party.leader-changed. {@code wasPublic} is present only when the change cleared the open flag, so
     * the new leader can be told the party is private again.
     */
    private static Map<String, Object> leaderChangedPayload(UUID previousLeaderId, UUID leaderId, boolean wasPublic) {
        if (!wasPublic) {
            return Map.of("previousLeaderId", previousLeaderId, "leaderId", leaderId);
        }
        return Map.of("previousLeaderId", previousLeaderId, "leaderId", leaderId, "wasPublic", true);
    }

    /** Rounds up to whole seconds, the unit of Retry-After. */
    private static Duration wholeSeconds(Duration wait) {
        return Duration.ofSeconds((wait.toMillis() + 999) / 1000);
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

    /** The private limit for a private party, the public limit for an open one. A closed party above the private limit stays full. */
    private void ensureRoom(Party party) {
        int limit = party.publicParty() ? settings.getPublicMaxSize() : settings.getMaxSize();
        if (party.members().size() >= limit) {
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
