package net.onelitefeather.quelea.velocity.player;

import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Records what the party logic does to a player. Connections succeed unless a different outcome is set.
 */
public final class FakeLocalPlayer implements LocalPlayer {

    private final UUID id;
    private final String name;
    private final Locale locale;
    private String currentServer;
    private ConnectOutcome outcome = ConnectOutcome.CONNECTED;
    private final List<Component> sent = new ArrayList<>();
    private final List<String> connectRequests = new ArrayList<>();

    public FakeLocalPlayer(String name, String currentServer) {
        this(UUID.nameUUIDFromBytes(name.getBytes()), name, Locale.ENGLISH, currentServer);
    }

    public FakeLocalPlayer(UUID id, String name, Locale locale, String currentServer) {
        this.id = id;
        this.name = name;
        this.locale = locale;
        this.currentServer = currentServer;
    }

    public FakeLocalPlayer refusingConnections() {
        this.outcome = ConnectOutcome.REFUSED;
        return this;
    }

    public List<Component> sent() {
        return List.copyOf(sent);
    }

    public List<String> connectRequests() {
        return List.copyOf(connectRequests);
    }

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Locale locale() {
        return locale;
    }

    @Override
    public Optional<String> currentServer() {
        return Optional.ofNullable(currentServer);
    }

    @Override
    public void send(Component message) {
        sent.add(message);
    }

    @Override
    public CompletableFuture<ConnectOutcome> connect(String server) {
        connectRequests.add(server);
        if (outcome == ConnectOutcome.CONNECTED) {
            currentServer = server;
        }
        return CompletableFuture.completedFuture(outcome);
    }
}
