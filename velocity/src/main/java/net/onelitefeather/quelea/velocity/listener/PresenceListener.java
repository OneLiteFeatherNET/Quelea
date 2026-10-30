package net.onelitefeather.quelea.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.onelitefeather.quelea.velocity.error.FailureReports;
import net.onelitefeather.quelea.velocity.http.Party;
import net.onelitefeather.quelea.velocity.http.PlayerRef;
import net.onelitefeather.quelea.velocity.http.QueleaGateway;
import net.onelitefeather.quelea.velocity.http.QueleaProblemException;
import net.onelitefeather.quelea.velocity.http.QueleaUnavailableException;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Reports logins and disconnects, and warps a party when its leader moves between servers. Service calls run on the
 * executor, never on Velocity's event thread.
 */
public final class PresenceListener {

    private final QueleaGateway gateway;
    private final Executor executor;
    private final String proxyId;
    private final FailureReports failures;
    private final Logger logger;

    public PresenceListener(QueleaGateway gateway, Executor executor, String proxyId, FailureReports failures,
                            Logger logger) {
        this.gateway = gateway;
        this.executor = executor;
        this.proxyId = proxyId;
        this.failures = failures;
        this.logger = logger;
    }

    @Subscribe
    public void onLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        PlayerRef ref = new PlayerRef(player.getUniqueId().toString(), player.getUsername());
        submit("login of " + ref.name(), () -> gateway.login(proxyId, ref.id(), ref.name()));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        String playerId = event.getPlayer().getUniqueId().toString();
        submit("disconnect of " + event.getPlayer().getUsername(), () -> gateway.disconnect(proxyId, playerId));
    }

    /**
     * A leader's move from a server to another one warps the party to the new server, whatever the server is. The
     * first connection after login has no previous server and warps nothing.
     */
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Optional<RegisteredServer> previous = event.getPreviousServer();
        if (previous.isEmpty()) {
            return;
        }
        String server = event.getServer().getServerInfo().getName();
        String origin = previous.get().getServerInfo().getName();
        Player player = event.getPlayer();
        String playerId = player.getUniqueId().toString();
        submit("warp of " + player.getUsername(), () -> warpIfLeader(playerId, server, origin));
    }

    private void warpIfLeader(String playerId, String server, String origin) {
        Party party = gateway.partyOf(playerId).orElse(null);
        if (party == null || !playerId.equals(party.leaderId())) {
            return;
        }
        gateway.warp(party.id(), playerId, server, origin);
    }

    private void submit(String action, Runnable call) {
        executor.execute(() -> {
            try {
                call.run();
            } catch (QueleaProblemException | QueleaUnavailableException e) {
                logger.warn("Presence update failed ({}): {}", action, e.getMessage());
                failures.failure(e, null);
            } catch (RuntimeException e) {
                logger.error("Presence update failed unexpectedly ({})", action, e);
                failures.failure(e, null);
            }
        });
    }
}
