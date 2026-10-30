package net.onelitefeather.quelea.backend.party;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/** A player's open request to join a private party. It lives in the store until it is answered or expires. */
@Serdeable
public record JoinRequest(UUID partyId, UUID requesterId, String requesterName, Instant expiresAt) {
}
