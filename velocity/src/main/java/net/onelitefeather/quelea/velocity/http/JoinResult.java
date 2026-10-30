package net.onelitefeather.quelea.velocity.http;

import java.time.Instant;

/**
 * What {@code /party request} got back: the party the player joined directly, or an open request to a private party
 * that is valid until {@code expiresAt}.
 */
public sealed interface JoinResult permits JoinResult.Joined, JoinResult.Sent {

    /** The party is public: the player is a member now (HTTP 200). */
    record Joined(Party party) implements JoinResult {
    }

    /** The leader has been asked and can answer until {@code expiresAt} (HTTP 201). */
    record Sent(Instant expiresAt) implements JoinResult {
    }
}
