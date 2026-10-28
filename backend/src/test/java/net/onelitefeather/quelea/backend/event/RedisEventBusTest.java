package net.onelitefeather.quelea.backend.event;

import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.serde.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** An event is a hint: a failed publish must not fail the request whose state change is already saved. */
class RedisEventBusTest {

    @Test
    void failedPublishIsCountedInsteadOfThrown() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RedisEventBus bus = new RedisEventBus(connectionWhosePublishFails(), null,
                ObjectMapper.create(Map.of()), registry, null);
        PartyEvent event = new PartyEvent(UUID.randomUUID(), "party.chat", UUID.randomUUID(), Instant.EPOCH,
                UUID.randomUUID(), List.of(), Map.of("message", "hi"), null);

        assertDoesNotThrow(() -> bus.publish(event), "publishing must swallow a Dragonfly failure");
        assertEquals(1.0, registry.get("quelea.events.failed").tag("type", "party.chat").counter().count(),
                "the failed publish must be counted");
    }

    @SuppressWarnings("unchecked")
    private static StatefulRedisConnection<String, String> connectionWhosePublishFails() {
        RedisCommands<String, String> commands = (RedisCommands<String, String>) Proxy.newProxyInstance(
                RedisCommands.class.getClassLoader(), new Class<?>[]{RedisCommands.class},
                (proxy, method, args) -> {
                    throw new RedisConnectionException("Dragonfly is down");
                });
        return (StatefulRedisConnection<String, String>) Proxy.newProxyInstance(
                StatefulRedisConnection.class.getClassLoader(), new Class<?>[]{StatefulRedisConnection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("sync")) {
                        return commands;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
