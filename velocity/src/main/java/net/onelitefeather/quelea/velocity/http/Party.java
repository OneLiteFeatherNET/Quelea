package net.onelitefeather.quelea.velocity.http;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A party as the service reports it.
 *
 * @param id        the party's UUID as a string
 * @param leaderId  the leader's UUID as a string
 * @param members   the members, leader included
 * @param createdAt when the party was created
 */
public record Party(String id, String leaderId, List<Member> members, Instant createdAt) {

    public Party {
        members = List.copyOf(members);
    }

    /**
     * @param name the member's name, compared ignoring case
     * @return the member with that name, if any
     */
    public Optional<Member> memberNamed(String name) {
        return members.stream().filter(member -> member.name().equalsIgnoreCase(name)).findFirst();
    }
}
