package net.onelitefeather.quelea.backend.store.redis;

import net.onelitefeather.quelea.backend.presence.PlayerPresence;
import net.onelitefeather.quelea.backend.support.MutableClock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
class RedisPresenceStoreTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private static DragonflyFixture dragonfly;

    private RedisPresenceStore store;

    @BeforeAll
    static void startDragonfly() {
        dragonfly = DragonflyFixture.start();
    }

    @AfterAll
    static void stopDragonfly() {
        dragonfly.close();
    }

    @BeforeEach
    void freshStore() {
        dragonfly.flushAll();
        store = new RedisPresenceStore(dragonfly.connection(), new RedisKeys("quelea:"),
                io.micronaut.serde.ObjectMapper.getDefault(), new MutableClock(NOW));
    }

    @Test
    void putThenFindReturnsPresence() {
        PlayerPresence alpha = presence(UUID.randomUUID(), "Alpha", "proxy-1");

        store.put(alpha);

        assertEquals(alpha, store.find(alpha.id()).orElseThrow());
    }

    @Test
    void findByNameIgnoresCase() {
        PlayerPresence alpha = presence(UUID.randomUUID(), "Alpha", "proxy-1");
        store.put(alpha);

        assertEquals(alpha, store.findByName("ALPHA").orElseThrow());
    }

    @Test
    void movingToAnotherProxyUpdatesBothProxySets() {
        UUID player = UUID.randomUUID();
        store.put(presence(player, "Alpha", "proxy-1"));

        store.put(presence(player, "Alpha", "proxy-2"));

        assertFalse(store.playersOf("proxy-1").contains(player));
        assertEquals(Set.of(player), store.playersOf("proxy-2"));
    }

    @Test
    void renamingDropsTheOldNameIndexEntry() {
        UUID player = UUID.randomUUID();
        store.put(presence(player, "Alpha", "proxy-1"));

        store.put(presence(player, "Bravo", "proxy-1"));

        assertTrue(store.findByName("alpha").isEmpty());
        assertEquals(player, store.findByName("bravo").orElseThrow().id());
    }

    @Test
    void removeClearsPresenceNameIndexAndProxySet() {
        UUID player = UUID.randomUUID();
        store.put(presence(player, "Alpha", "proxy-1"));

        store.remove(player);

        assertTrue(store.find(player).isEmpty());
        assertTrue(store.findByName("Alpha").isEmpty());
        assertTrue(store.playersOf("proxy-1").isEmpty());
    }

    @Test
    void proxyIsAliveUntilItsHeartbeatTtlPasses() {
        store.touchProxy("proxy-1", Duration.ofMillis(500));

        assertTrue(store.isAlive("proxy-1"));
        assertTrue(DragonflyFixture.awaitTrue(() -> !store.isAlive("proxy-1"), Duration.ofSeconds(2)),
                "the proxy should be dead once its heartbeat expires");
    }

    @Test
    void proxiesListsEveryTouchedProxy() {
        store.touchProxy("proxy-1", Duration.ofSeconds(45));
        store.touchProxy("proxy-2", Duration.ofSeconds(45));

        assertEquals(Set.of("proxy-1", "proxy-2"), store.proxies());
    }

    @Test
    void forgetProxyClearsItsPlayersHeartbeatAndRegistration() {
        UUID player = UUID.randomUUID();
        store.put(presence(player, "Alpha", "proxy-1"));
        store.touchProxy("proxy-1", Duration.ofSeconds(45));

        store.forgetProxy("proxy-1");

        assertFalse(store.isAlive("proxy-1"));
        assertFalse(store.proxies().contains("proxy-1"));
        assertTrue(store.playersOf("proxy-1").isEmpty());
    }

    private static PlayerPresence presence(UUID id, String name, String proxyId) {
        return new PlayerPresence(id, name, proxyId, NOW);
    }
}
