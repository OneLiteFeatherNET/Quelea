package net.onelitefeather.quelea.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import net.onelitefeather.quelea.velocity.follow.FollowPolicy;
import net.onelitefeather.quelea.velocity.http.Party;
import net.onelitefeather.quelea.velocity.http.PlayerRef;
import net.onelitefeather.quelea.velocity.http.QueleaGateway;
import net.onelitefeather.quelea.velocity.http.QueleaProblemException;
import net.onelitefeather.quelea.velocity.http.QueleaUnavailableException;
import org.slf4j.Logger;

import java.util.concurrent.ExecutorService;

/**
 * Reports logins and disconnects, and warps a party when its leader moves to a game. Service calls run on the
 * executor, never on Velocity's event thread.
 */
public final class PresenceListener {

    private final QueleaGateway gateway;
    private final ExecutorService executor;
    private final String proxyId;
    private final FollowPolicy follow;
    private final Logger logger;

    public PresenceListener(QueleaGateway gateway, ExecutorService executor, String proxyId, FollowPolicy follow,
                            Logger logger) {
        this.gateway = gateway;
        this.executor = executor;
        this.proxyId = proxyId;
        this.follow = follow;
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

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        String server = event.getServer().getServerInfo().getName();
        if (!follow.shouldTriggerWarp(server)) {
            return;
        }
        Player player = event.getPlayer();
        String playerId = player.getUniqueId().toString();
        submit("warp of " + player.getUsername(), () -> warpIfLeader(playerId, server));
    }

    private void warpIfLeader(String playerId, String server) {
        Party party = gateway.partyOf(playerId).orElse(null);
        if (party == null || !playerId.equals(party.leaderId())) {
            return;
        }
        gateway.warp(party.id(), playerId, server);
    }

    private void submit(String action, Runnable call) {
        executor.execute(() -> {
            try {
                call.run();
            } catch (QueleaProblemException | QueleaUnavailableException e) {
                logger.warn("Presence update failed ({}): {}", action, e.getMessage());
            } catch (RuntimeException e) {
                logger.error("Presence update failed unexpectedly ({})", action, e);
            }
        });
    }
}
