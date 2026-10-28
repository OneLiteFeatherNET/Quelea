package net.onelitefeather.quelea.backend.event;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-process fan-out of party events to every open SSE stream of this replica. Delivery is best effort: an event
 * emitted while no stream is open is dropped, which matches the at-most-once contract of the event stream.
 */
@Singleton
public class EventBroadcaster {

    private final Sinks.Many<PartyEvent> sink = Sinks.many().multicast().directBestEffort();
    private final AtomicInteger streams = new AtomicInteger();

    public EventBroadcaster(MeterRegistry registry) {
        Gauge.builder("quelea.sse.streams", streams, AtomicInteger::get)
                .description("Open event streams on this replica")
                .register(registry);
    }

    /** Hands the event to every open stream. Safe to call from any thread. */
    public void deliver(PartyEvent event) {
        // The sink is not safe for concurrent emitters (Redis listener thread and request threads both deliver).
        synchronized (sink) {
            sink.tryEmitNext(event);
        }
    }

    /** One subscription is one open stream; the stream count goes down when it terminates. */
    public Flux<PartyEvent> stream() {
        return sink.asFlux()
                .doOnSubscribe(subscription -> streams.incrementAndGet())
                .doFinally(signal -> streams.decrementAndGet());
    }

    public int streamCount() {
        return streams.get();
    }
}
