package net.onelitefeather.quelea.backend.store.redis;

import io.micronaut.serde.ObjectMapper;
import net.onelitefeather.quelea.backend.idempotency.IdempotencyRecord;
import net.onelitefeather.quelea.backend.idempotency.StoredResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
class RedisIdempotencyStoreTest {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static DragonflyFixture dragonfly;

    private RedisIdempotencyStore store;

    @BeforeAll
    static void startDragonfly() {
        dragonfly = DragonflyFixture.start();
    }

    @AfterAll
    static void stopDragonfly() {
        if (dragonfly != null) {
            dragonfly.close();
        }
    }

    @BeforeEach
    void freshStore() {
        dragonfly.flushAll();
        store = new RedisIdempotencyStore(dragonfly.connection(), new RedisKeys("quelea:"), ObjectMapper.getDefault());
    }

    @Test
    void claimOfFreeKeyStoresTheMarker() {
        IdempotencyRecord marker = new IdempotencyRecord("POST /v1/x abc", null);

        assertTrue(store.claim("client-a", "key-1", marker, TTL).isEmpty(), "a free key must be claimed");
        assertEquals(marker, store.claim("client-a", "key-1", marker, TTL).orElseThrow(),
                "a second claim must find the marker");
    }

    @Test
    void completedRecordIsReturnedToTheSameClientOnly() {
        IdempotencyRecord completed = new IdempotencyRecord("POST /v1/x abc",
                new StoredResponse(201, "application/json", "\"1\"", "{\"id\":\"p\"}"));
        store.claim("client-a", "key-1", new IdempotencyRecord("POST /v1/x abc", null), TTL);
        store.complete("client-a", "key-1", completed, TTL);

        assertEquals(completed, store.claim("client-a", "key-1", completed, TTL).orElseThrow(),
                "the same client must find the stored response");
        assertTrue(store.claim("client-b", "key-1", completed, TTL).isEmpty(),
                "another client's key must be free");
    }

    @Test
    void releaseFreesTheKey() {
        IdempotencyRecord marker = new IdempotencyRecord("POST /v1/x abc", null);
        store.claim("client-a", "key-1", marker, TTL);

        store.release("client-a", "key-1");

        assertFalse(store.claim("client-a", "key-1", marker, TTL).isPresent(),
                "a released key must be claimable again");
    }
}
