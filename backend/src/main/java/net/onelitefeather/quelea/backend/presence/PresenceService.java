package net.onelitefeather.quelea.backend.presence;

import jakarta.inject.Singleton;
import net.onelitefeather.quelea.backend.party.PartyService;
import net.onelitefeather.quelea.backend.party.port.PartyStore;
import net.onelitefeather.quelea.backend.party.port.PresenceStore;
import net.onelitefeather.quelea.backend.problem.PartyException;
import net.onelitefeather.quelea.backend.problem.PartyProblem;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Singleton
public class PresenceService {

    private final PresenceStore presence;
    private final PartyStore parties;
    private final PartyService partyService;
    private final PresenceSettings settings;
    private final Clock clock;

    public PresenceService(PresenceStore presence, PartyStore parties, PartyService partyService,
                           PresenceSettings settings, Clock clock) {
        this.presence = presence;
        this.parties = parties;
        this.partyService = partyService;
        this.settings = settings;
        this.clock = clock;
    }

    public void login(String proxyId, UUID playerId, String name) {
        presence.put(new PlayerPresence(playerId, name, proxyId, clock.instant()));
        presence.touchProxy(proxyId, settings.getProxyTimeout());
    }

    /** Ignored unless the stored presence is on {@code proxyId}, so a late disconnect cannot undo a re-login. */
    public void disconnect(String proxyId, UUID playerId) {
        Optional<PlayerPresence> current = presence.find(playerId);
        if (current.isEmpty() || !current.get().proxyId().equals(proxyId)) {
            return;
        }
        presence.remove(playerId);
        leaveParty(playerId);
        parties.deleteInvitesOf(playerId);
    }

    /** Makes the proxy's player set equal to {@code players}; players left out are disconnected. */
    public void snapshot(String proxyId, List<PlayerRef> players) {
        Set<UUID> listed = new HashSet<>();
        for (PlayerRef player : players) {
            listed.add(player.id());
            presence.put(new PlayerPresence(player.id(), player.name(), proxyId, clock.instant()));
        }
        for (UUID stale : new HashSet<>(presence.playersOf(proxyId))) {
            if (!listed.contains(stale)) {
                disconnect(proxyId, stale);
            }
        }
        presence.touchProxy(proxyId, settings.getProxyTimeout());
    }

    public void heartbeat(String proxyId) {
        presence.touchProxy(proxyId, settings.getProxyTimeout());
    }

    /** Disconnects every player of every proxy without a live heartbeat, then forgets those proxies. */
    public void reap() {
        for (String proxyId : presence.proxies()) {
            if (presence.isAlive(proxyId)) {
                continue;
            }
            for (UUID playerId : new HashSet<>(presence.playersOf(proxyId))) {
                disconnect(proxyId, playerId);
            }
            presence.forgetProxy(proxyId);
        }
    }

    private void leaveParty(UUID playerId) {
        try {
            partyService.leave(playerId, "disconnect");
        } catch (PartyException e) {
            if (e.problem() != PartyProblem.NOT_IN_PARTY) {
                throw e;
            }
        }
    }
}
