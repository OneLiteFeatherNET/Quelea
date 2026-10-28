package net.onelitefeather.quelea.backend.api;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.onelitefeather.quelea.backend.event.EventBroadcaster;
import net.onelitefeather.quelea.backend.event.PartyEvent;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventControllerTest {

    @Test
    void missedKeepAliveDoesNotEndTheStream() {
        EventController controller = new EventController(new EventBroadcaster(new SimpleMeterRegistry(), 256),
                Duration.ofSeconds(15), new SimpleMeterRegistry());

        // Requests nothing, so every keep-alive tick arrives while the client is not reading. Ten minutes are 40 ticks,
        // more than the 32 the merge buffers before a missed tick used to end the stream.
        StepVerifier.withVirtualTime(() -> controller.stream(null, "proxy-1"), 0)
                .expectSubscription()
                .thenAwait(Duration.ofMinutes(10))
                .expectNoEvent(Duration.ofSeconds(1))
                .thenCancel()
                .verify();
    }

    @Test
    void stalledStreamEndsAndOverflowIsCounted() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        EventBroadcaster broadcaster = new EventBroadcaster(registry, 4);
        EventController controller = new EventController(broadcaster, Duration.ofMinutes(10), registry);

        // Well past the merge buffer (32) plus the stream buffer (4), so the stalled stream overflows.
        StepVerifier.create(controller.stream("lobby-1", null), 0)
                .expectSubscription()
                .then(() -> {
                    for (int i = 0; i < 100; i++) {
                        broadcaster.deliver(chat());
                    }
                })
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        assertEquals(1.0, registry.get("quelea.sse.disconnects").tag("reason", "overflow").counter().count(),
                "the overflow must be counted once");
    }

    @Test
    void clientIdIsTheLabel() {
        assertEquals("lobby-1", EventController.label("lobby-1", "proxy-1"), "clientId must name the client in logs");
    }

    @Test
    void proxyIdIsTheLabelWhenNoClientIdIsGiven() {
        assertEquals("proxy-1", EventController.label(null, "proxy-1"), "proxyId must keep working for the 0.1.0 plugin");
    }

    @Test
    void unlabelledWhenNeitherIsGiven() {
        assertEquals(EventController.UNLABELLED, EventController.label(null, null), "a stream without a label must still open");
    }

    private static PartyEvent chat() {
        return new PartyEvent(UUID.randomUUID(), "party.chat", UUID.randomUUID(),
                Instant.parse("2026-01-01T12:00:00Z"), null, List.of(), Map.of(), null);
    }
}
