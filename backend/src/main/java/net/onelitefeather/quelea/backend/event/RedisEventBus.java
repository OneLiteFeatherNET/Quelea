package net.onelitefeather.quelea.backend.event;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.serde.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import net.onelitefeather.quelea.backend.party.port.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Cross-replica fan-out over Redis pub/sub. Every replica publishes to {@value #CHANNEL} and subscribes to it, so an
 * event reaches the streams of all replicas, including the one that handled the request.
 */
@Singleton
@Requires(beans = StatefulRedisConnection.class)
public class RedisEventBus implements EventPublisher {

    public static final String CHANNEL = "quelea.events";

    private static final Logger LOG = LoggerFactory.getLogger(RedisEventBus.class);

    private final StatefulRedisConnection<String, String> connection;
    private final StatefulRedisPubSubConnection<String, String> subscriber;
    private final ObjectMapper mapper;
    private final MeterRegistry registry;
    private final EventFanout fanout;

    public RedisEventBus(StatefulRedisConnection<String, String> connection,
                         StatefulRedisPubSubConnection<String, String> subscriber,
                         ObjectMapper mapper,
                         MeterRegistry registry,
                         EventFanout fanout) {
        this.connection = connection;
        this.subscriber = subscriber;
        this.mapper = mapper;
        this.registry = registry;
        this.fanout = fanout;
    }

    @Override
    public void publish(PartyEvent event) {
        String json;
        try {
            json = mapper.writeValueAsString(event);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot serialise party event " + event.type(), e);
        }
        try {
            connection.sync().publish(CHANNEL, json);
        } catch (RuntimeException e) {
            // The state change is already saved and an event is only a hint: count it, never fail the request.
            LOG.warn("Could not publish party event {}: {}", event.type(), e.getMessage());
            Counter.builder("quelea.events.failed")
                    .description("Party events that could not be published, by type")
                    .tag("type", event.type())
                    .register(registry)
                    .increment();
            return;
        }
        Counter.builder("quelea.events.published")
                .description("Party events published, by type")
                .tag("type", event.type())
                .register(registry)
                .increment();
    }

    @EventListener
    void subscribe(StartupEvent event) {
        subscriber.addListener(new RedisPubSubAdapter<String, String>() {
            @Override
            public void message(String channel, String message) {
                fanout.onMessage(channel, message);
            }
        });
        subscriber.sync().subscribe(CHANNEL);
    }

    @PreDestroy
    void close() {
        subscriber.close();
    }
}
