package net.onelitefeather.quelea.backend.event;

import io.micronaut.serde.annotation.Serdeable;
import net.onelitefeather.quelea.backend.party.Member;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One change of a party. {@code members} and {@code leaderId} describe the party after the change;
 * {@code traceparent} is the W3C trace context of the request that caused it, or {@code null}.
 */
@Serdeable
public record PartyEvent(
        UUID id,
        String type,
        UUID partyId,
        Instant occurredAt,
        UUID leaderId,
        List<Member> members,
        Map<String, Object> payload,
        String traceparent
) {
}
