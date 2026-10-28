package net.onelitefeather.quelea.backend.event;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.serde.ObjectMapper;
import io.opentelemetry.api.OpenTelemetry;
import net.onelitefeather.quelea.backend.party.Member;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Two replicas (two broadcasters, two fan-outs) receive the same message from the channel. */
class EventFanoutTest {

    private static final String CHANNEL = RedisEventBus.CHANNEL;

    private final ObjectMapper mapper = ObjectMapper.create(Map.of());

    @Test
    void oneMessageReachesEveryReplicasStream() throws IOException {
        EventBroadcaster replicaOne = new EventBroadcaster(new SimpleMeterRegistry(), 256);
        EventBroadcaster replicaTwo = new EventBroadcaster(new SimpleMeterRegistry(), 256);
        List<PartyEvent> seenOne = new ArrayList<>();
        List<PartyEvent> seenTwo = new ArrayList<>();
        Disposable one = replicaOne.stream().subscribe(seenOne::add);
        Disposable two = replicaTwo.stream().subscribe(seenTwo::add);
        EventFanout fanoutOne = new EventFanout(replicaOne, mapper, OpenTelemetry.noop());
        EventFanout fanoutTwo = new EventFanout(replicaTwo, mapper, OpenTelemetry.noop());
        String message = mapper.writeValueAsString(invited());

        fanoutOne.onMessage(CHANNEL, message);
        fanoutTwo.onMessage(CHANNEL, message);

        assertEquals(2, seenOne.size() + seenTwo.size(), "one message per replica");
        assertEquals("party.invited", seenOne.get(0).type(), "replica one must deliver the invite");
        assertEquals("party.invited", seenTwo.get(0).type(), "replica two must deliver the invite");
        one.dispose();
        two.dispose();
    }

    @Test
    void deliveredEventKeepsItsIdPayloadAndTraceparent() throws IOException {
        EventBroadcaster broadcaster = new EventBroadcaster(new SimpleMeterRegistry(), 256);
        List<PartyEvent> seen = new ArrayList<>();
        Disposable subscription = broadcaster.stream().subscribe(seen::add);
        PartyEvent original = invited();
        EventFanout fanout = new EventFanout(broadcaster, mapper, OpenTelemetry.noop());

        fanout.onMessage(CHANNEL, mapper.writeValueAsString(original));

        PartyEvent delivered = seen.get(0);
        assertEquals(original.id(), delivered.id(), "the id must survive the round trip");
        assertEquals(original.partyId(), delivered.partyId(), "the party id must survive the round trip");
        assertEquals(original.traceparent(), delivered.traceparent(), "the traceparent must survive the round trip");
        assertEquals("Alice", delivered.payload().get("inviterName"), "the payload must survive the round trip");
        subscription.dispose();
    }

    @Test
    void unparseableMessageIsDroppedWithoutDelivery() {
        EventBroadcaster broadcaster = new EventBroadcaster(new SimpleMeterRegistry(), 256);
        List<PartyEvent> seen = new ArrayList<>();
        Disposable subscription = broadcaster.stream().subscribe(seen::add);
        EventFanout fanout = new EventFanout(broadcaster, mapper, OpenTelemetry.noop());

        boolean delivered = fanout.onMessage(CHANNEL, "{not json");

        assertFalse(delivered, "a message that does not parse must not count as delivered");
        assertTrue(seen.isEmpty(), "nothing may reach the streams for a broken message");
        subscription.dispose();
    }

    private static PartyEvent invited() {
        UUID leader = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        return new PartyEvent(
                UUID.randomUUID(),
                "party.invited",
                UUID.randomUUID(),
                now,
                leader,
                List.of(new Member(leader, "Alice", now)),
                Map.of("inviterName", "Alice", "targetName", "Bob", "expiresAt", now.plusSeconds(60).toString()),
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", 2);
    }
}
