package net.onelitefeather.quelea.velocity.http;

/**
 * A player as the proxy reports it in presence calls and snapshots.
 *
 * @param id   the player's UUID as a string
 * @param name the player's name
 */
public record PlayerRef(String id, String name) {
}
