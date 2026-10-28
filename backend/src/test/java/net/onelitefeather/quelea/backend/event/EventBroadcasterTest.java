package net.onelitefeather.quelea.backend.event;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventBroadcasterTest {

    @Test
    void streamCountFollowsSubscriptions() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        EventBroadcaster broadcaster = new EventBroadcaster(registry);

        Disposable first = broadcaster.stream().subscribe();
        Disposable second = broadcaster.stream().subscribe();

        assertEquals(2, broadcaster.streamCount(), "two open streams");
        assertEquals(2.0, registry.get("quelea.sse.streams").gauge().value(), "the gauge must show the open streams");
        first.dispose();
        second.dispose();
        assertEquals(0, broadcaster.streamCount(), "disposed streams are no longer counted");
    }

    @Test
    void everyOpenStreamReceivesTheSameEvent() {
        EventBroadcaster broadcaster = new EventBroadcaster(new SimpleMeterRegistry());
        List<PartyEvent> first = new ArrayList<>();
        List<PartyEvent> second = new ArrayList<>();
        Disposable a = broadcaster.stream().subscribe(first::add);
        Disposable b = broadcaster.stream().subscribe(second::add);
        PartyEvent event = new PartyEvent(UUID.randomUUID(), "party.chat", UUID.randomUUID(),
                Instant.parse("2026-01-01T12:00:00Z"), null, List.of(), Map.of(), null);

        broadcaster.deliver(event);

        assertEquals(List.of(event), first, "the first stream must receive the event");
        assertEquals(List.of(event), second, "the second stream must receive the event");
        a.dispose();
        b.dispose();
    }

    @Test
    void eventWithoutOpenStreamIsDropped() {
        EventBroadcaster broadcaster = new EventBroadcaster(new SimpleMeterRegistry());
        PartyEvent event = new PartyEvent(UUID.randomUUID(), "party.chat", UUID.randomUUID(),
                Instant.parse("2026-01-01T12:00:00Z"), null, List.of(), Map.of(), null);

        broadcaster.deliver(event);

        assertEquals(0, broadcaster.streamCount(), "delivery without subscribers must not open anything");
    }
}
