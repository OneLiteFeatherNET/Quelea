package net.onelitefeather.quelea.velocity.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory {@link LocalPlayers} for tests.
 */
public final class FakeLocalPlayers implements LocalPlayers {

    private final Map<UUID, LocalPlayer> players = new LinkedHashMap<>();

    public FakeLocalPlayers with(LocalPlayer... added) {
        for (LocalPlayer player : added) {
            players.put(player.id(), player);
        }
        return this;
    }

    @Override
    public Optional<LocalPlayer> find(UUID id) {
        return Optional.ofNullable(players.get(id));
    }

    @Override
    public Collection<LocalPlayer> all() {
        return new ArrayList<>(players.values());
    }
}
