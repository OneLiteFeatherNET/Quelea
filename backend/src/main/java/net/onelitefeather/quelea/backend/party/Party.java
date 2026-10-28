package net.onelitefeather.quelea.backend.party;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** A party in the order its members joined; the leader is one of the members. */
@Serdeable
public record Party(UUID id, UUID leaderId, List<Member> members, Instant createdAt) {

    public Party {
        members = List.copyOf(members);
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

    public Party withMembers(List<Member> newMembers) {
        return new Party(id, leaderId, newMembers, createdAt);
    }

    public Party withLeader(UUID newLeaderId) {
        return new Party(id, newLeaderId, members, createdAt);
    }
}
