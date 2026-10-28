package net.onelitefeather.quelea.backend.event;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import reactor.core.Disposable;
import reactor.core.publisher.BaseSubscriber;

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
        EventBroadcaster broadcaster = new EventBroadcaster(registry, 256);

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
        EventBroadcaster broadcaster = new EventBroadcaster(new SimpleMeterRegistry(), 256);
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
        EventBroadcaster broadcaster = new EventBroadcaster(new SimpleMeterRegistry(), 256);
        PartyEvent event = new PartyEvent(UUID.randomUUID(), "party.chat", UUID.randomUUID(),
                Instant.parse("2026-01-01T12:00:00Z"), null, List.of(), Map.of(), null);

        broadcaster.deliver(event);

        assertEquals(0, broadcaster.streamCount(), "delivery without subscribers must not open anything");
    }

    @Test
    void stalledStreamIsClosedWhileEveryOtherStreamKeepsReceiving() {
        EventBroadcaster broadcaster = new EventBroadcaster(new SimpleMeterRegistry(), 4);
        List<Throwable> stalledErrors = new ArrayList<>();
        List<PartyEvent> healthy = new ArrayList<>();
        // Requests nothing, like a client whose connection stopped draining.
        BaseSubscriber<PartyEvent> stalled = new BaseSubscriber<>() {
            @Override
            protected void hookOnSubscribe(Subscription subscription) {
            }

            @Override
            protected void hookOnError(Throwable error) {
                stalledErrors.add(error);
            }
        };
        broadcaster.stream().subscribe(stalled);
        Disposable reading = broadcaster.stream().subscribe(healthy::add);
        List<PartyEvent> events = new ArrayList<>();
        // One more than the stalled stream's buffer of 4.
        for (int i = 0; i < 5; i++) {
            events.add(new PartyEvent(UUID.randomUUID(), "party.chat", UUID.randomUUID(),
                    Instant.parse("2026-01-01T12:00:00Z"), null, List.of(), Map.of(), null));
        }

        events.forEach(broadcaster::deliver);

        assertEquals(1, stalledErrors.size(), "a stalled stream must be closed once its buffer overflows, errors: " + stalledErrors);
        assertEquals(events, healthy, "a reading stream must receive every event while another stream stalls");
        reading.dispose();
    }
}
