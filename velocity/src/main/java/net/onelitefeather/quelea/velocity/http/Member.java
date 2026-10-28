package net.onelitefeather.quelea.velocity.http;

import java.time.Instant;

/**
 * A party member as the service reports it.
 *
 * @param id       the player's UUID as a string
 * @param name     the player's name when they joined
 * @param joinedAt when the player joined the party
 */
public record Member(String id, String name, Instant joinedAt) {
}
