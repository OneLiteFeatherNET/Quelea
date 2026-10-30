package net.onelitefeather.quelea.backend.party;

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
 */
@Serdeable
public record Party(UUID id, UUID leaderId, List<Member> members, Instant createdAt, long version, Instant aloneSince) {

    public Party {
        members = List.copyOf(members);
    }

    /** A party without an alone-since time, as before the field existed. */
    public Party(UUID id, UUID leaderId, List<Member> members, Instant createdAt, long version) {
        this(id, leaderId, members, createdAt, version, null);
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
        return new Party(id, leaderId, newMembers, createdAt, version + 1, null);
    }

    /** The party after a change of its leader: the next version. */
    public Party withLeader(UUID newLeaderId) {
        return new Party(id, newLeaderId, members, createdAt, version + 1, aloneSince);
    }
}
