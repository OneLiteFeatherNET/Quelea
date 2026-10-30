package net.onelitefeather.quelea.velocity.player;

import net.kyori.adventure.text.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A player connected to this proxy, as far as the party plugin needs them.
 */
public interface LocalPlayer {

    /** The Velocity permission a leader needs to open a party to everyone. Closing needs none. */
    String PUBLIC_PARTY_PERMISSION = "quelea.party.public";

    UUID id();

    /**
     * @return whether the player holds the Velocity permission {@code permission}
     */
    boolean hasPermission(String permission);

    String name();

    Locale locale();

    /**
     * @return the name of the server the player is on, empty while they are between servers
     */
    Optional<String> currentServer();

    void send(Component message);

    /**
     * Connects the player to a server by name. The future completes off the calling thread.
     */
    CompletableFuture<ConnectOutcome> connect(String server);
}
