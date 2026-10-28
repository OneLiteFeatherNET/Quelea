package net.onelitefeather.quelea.velocity.http;

import java.time.Instant;

/**
 * A pending party invite.
 *
 * @param partyId     the party the invite is for
 * @param inviterId   the inviting leader's UUID as a string
 * @param inviterName the inviting leader's name
 * @param targetId    the invited player's UUID as a string
 * @param targetName  the invited player's name
 * @param expiresAt   when the invite stops being valid
 */
public record Invite(String partyId, String inviterId, String inviterName, String targetId, String targetName,
                     Instant expiresAt) {
}
