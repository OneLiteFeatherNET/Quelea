package net.onelitefeather.quelea.backend.event;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import net.onelitefeather.quelea.backend.party.port.EventPublisher;

/** Delivers events straight to this replica's streams. Used when no Redis is configured (tests, local runs). */
@Singleton
@Requires(missingBeans = RedisEventBus.class)
public class LocalEventBus implements EventPublisher {

    private final EventBroadcaster broadcaster;
    private final MeterRegistry registry;

    public LocalEventBus(EventBroadcaster broadcaster, MeterRegistry registry) {
        this.broadcaster = broadcaster;
        this.registry = registry;
    }

    @Override
    public void publish(PartyEvent event) {
        Counter.builder("quelea.events.published")
                .description("Party events published, by type")
                .tag("type", event.type())
                .register(registry)
                .increment();
        broadcaster.deliver(event);
    }
}
