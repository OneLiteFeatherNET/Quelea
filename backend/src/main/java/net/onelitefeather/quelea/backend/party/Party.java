package net.onelitefeather.quelea.backend.party;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A party in the order its members joined; the leader is one of the members. {@code version} is 1 when the party is
 * created and grows by one with every change of the party's state; it is the basis of the HTTP entity tag.
 * {@code aloneSince} is the start of the current alone period (one member), or {@code null} when the party has two or
 * more members or was stored before the field existed; {@link #aloneStart()} resolves the null case to the creation.
 * {@code publicParty} is the open flag, serialised as {@code public}: a public party admits online players without a
 * request. It is cleared whenever the leader changes, and reads as false in parties stored before it existed.
 */
@Serdeable
public record Party(UUID id, UUID leaderId, List<Member> members, Instant createdAt, long version, Instant aloneSince,
                    @JsonProperty("public") boolean publicParty) {

    public Party {
        members = List.copyOf(members);
    }

    /** A private party without an alone-since time, as before the fields existed. */
    public Party(UUID id, UUID leaderId, List<Member> members, Instant createdAt, long version) {
        this(id, leaderId, members, createdAt, version, null, false);
    }

    /** A private party with the given alone-since time. */
    public Party(UUID id, UUID leaderId, List<Member> members, Instant createdAt, long version, Instant aloneSince) {
        this(id, leaderId, members, createdAt, version, aloneSince, false);
    }

    /** When the current alone period began: the stored alone-since time, or the creation for a legacy party. */
    public Instant aloneStart() {
        return aloneSince != null ? aloneSince : createdAt;
    }

    public boolean isMember(UUID playerId) {
        return member(playerId).isPresent();
    }

    public Optional<Member> member(UUID playerId) {
        return members.stream().filter(member -> member.id().equals(playerId)).findFirst();
    }

    public boolean isLeader(UUID playerId) {
        return leaderId.equals(playerId);
    }

    /** The party after a join: the next version, with no alone period (a join ends the one before). */
    public Party withMembers(List<Member> newMembers) {
        return new Party(id, leaderId, newMembers, createdAt, version + 1, null, publicParty);
    }

    /** The party after a change of its leader: the next version. A change of leader closes the party. */
    public Party withLeader(UUID newLeaderId) {
        return new Party(id, newLeaderId, members, createdAt, version + 1, aloneSince, false);
    }

    /** The party after the leader opened or closed it: the next version. */
    public Party withPublic(boolean open) {
        return new Party(id, leaderId, members, createdAt, version + 1, aloneSince, open);
    }
}
