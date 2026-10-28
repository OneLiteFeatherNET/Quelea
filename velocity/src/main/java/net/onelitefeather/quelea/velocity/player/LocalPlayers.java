package net.onelitefeather.quelea.velocity.player;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * The players this proxy can see. The seam between the party logic and Velocity, so the logic can be tested with
 * fakes.
 */
public interface LocalPlayers {

    Optional<LocalPlayer> find(UUID id);

    Collection<LocalPlayer> all();
}
