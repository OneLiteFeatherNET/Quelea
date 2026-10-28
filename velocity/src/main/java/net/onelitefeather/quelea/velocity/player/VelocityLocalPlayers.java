package net.onelitefeather.quelea.velocity.player;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * {@link LocalPlayers} over the Velocity proxy.
 */
public final class VelocityLocalPlayers implements LocalPlayers {

    private final ProxyServer proxy;

    public VelocityLocalPlayers(ProxyServer proxy) {
        this.proxy = proxy;
    }

    @Override
    public Optional<LocalPlayer> find(UUID id) {
        return proxy.getPlayer(id).map(this::wrap);
    }

    @Override
    public Collection<LocalPlayer> all() {
        return proxy.getAllPlayers().stream().map(this::wrap).toList();
    }

    /**
     * @param player a Velocity player
     * @return the seam implementation for that player
     */
    public LocalPlayer wrap(Player player) {
        return new VelocityPlayer(player, proxy);
    }

    private record VelocityPlayer(Player player, ProxyServer proxy) implements LocalPlayer {

        @Override
        public UUID id() {
            return player.getUniqueId();
        }

        @Override
        public String name() {
            return player.getUsername();
        }

        @Override
        public Locale locale() {
            return player.getEffectiveLocale();
        }

        @Override
        public Optional<String> currentServer() {
            return player.getCurrentServer().map(connection -> connection.getServerInfo().getName());
        }

        @Override
        public void send(Component message) {
            player.sendMessage(message);
        }

        @Override
        public CompletableFuture<ConnectOutcome> connect(String server) {
            Optional<RegisteredServer> target = proxy.getServer(server);
            if (target.isEmpty()) {
                return CompletableFuture.completedFuture(ConnectOutcome.UNKNOWN_SERVER);
            }
            return player.createConnectionRequest(target.get()).connect()
                    .handle((result, error) -> error == null && result.isSuccessful()
                            ? ConnectOutcome.CONNECTED
                            : ConnectOutcome.REFUSED);
        }
    }
}
