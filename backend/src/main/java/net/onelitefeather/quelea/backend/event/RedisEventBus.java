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

import java.io.IOException;

/**
 * Cross-replica fan-out over Redis pub/sub. Every replica publishes to {@value #CHANNEL} and subscribes to it, so an
 * event reaches the streams of all replicas, including the one that handled the request.
 */
@Singleton
@Requires(beans = StatefulRedisConnection.class)
public class RedisEventBus implements EventPublisher {

    public static final String CHANNEL = "quelea.events";

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
        connection.sync().publish(CHANNEL, json);
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
