package net.onelitefeather.quelea.backend.api;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.onelitefeather.quelea.backend.presence.PlayerPresence;
import net.onelitefeather.quelea.backend.support.InMemoryPresenceStore;
import net.onelitefeather.quelea.backend.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayersOnlineGaugeTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Test
    void gaugeSumsPlayersAcrossProxies() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        InMemoryPresenceStore presence = new InMemoryPresenceStore(new MutableClock(NOW));
        new PlayersOnlineGauge(meters, presence);
        for (int i = 0; i < 3; i++) {
            presence.put(new PlayerPresence(UUID.randomUUID(), "P1-" + i, "proxy-1", NOW));
        }
        for (int i = 0; i < 2; i++) {
            presence.put(new PlayerPresence(UUID.randomUUID(), "P2-" + i, "proxy-2", NOW));
        }

        assertEquals(5.0, meters.get("quelea.players.online").gauge().value(),
                "three players on proxy-1 and two on proxy-2 must read as five online");
    }
}
