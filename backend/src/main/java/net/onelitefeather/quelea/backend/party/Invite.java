package net.onelitefeather.quelea.backend.party;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

@Serdeable
public record Invite(
        UUID partyId,
        UUID inviterId,
        String inviterName,
        UUID targetId,
        String targetName,
        Instant expiresAt
) {
}
