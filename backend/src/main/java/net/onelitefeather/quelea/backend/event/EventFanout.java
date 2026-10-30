package net.onelitefeather.quelea.backend.event;

import io.micronaut.serde.ObjectMapper;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Turns one message of the {@code quelea.events} channel into a delivery to this replica's streams. Every delivery
 * runs in a {@value #SPAN_NAME} span that links to the request span that caused the event. It holds no connection,
 * so the message handling can be tested without Redis.
 */
@Singleton
public class EventFanout {

    public static final String SPAN_NAME = "quelea.event.fanout";

    private static final Logger LOG = LoggerFactory.getLogger(EventFanout.class);

    private final EventBroadcaster broadcaster;
    private final ObjectMapper mapper;
    private final Tracer tracer;

    public EventFanout(EventBroadcaster broadcaster, ObjectMapper mapper, OpenTelemetry openTelemetry) {
        this.broadcaster = broadcaster;
        this.mapper = mapper;
        this.tracer = openTelemetry.getTracer("net.onelitefeather.quelea");
    }

    /** Delivers the event carried by {@code json}; returns false (and logs) when the message does not parse. */
    public boolean onMessage(String channel, String json) {
        PartyEvent event;
        try {
            event = mapper.readValue(json, PartyEvent.class);
        } catch (IOException | RuntimeException e) {
            // The parser's message quotes fragments of the payload, which carries player names: log the type only.
            LOG.warn("Dropping unparseable message on channel {}: {}", channel, e.getClass().getSimpleName());
            return false;
        }
        if (event == null) {
            LOG.warn("Dropping empty message on channel {}", channel);
            return false;
        }
        SpanContext cause = TraceParents.parse(event.traceparent());
        var builder = tracer.spanBuilder(SPAN_NAME)
                .setAttribute("quelea.event.type", String.valueOf(event.type()))
                .setAttribute("quelea.party.id", String.valueOf(event.partyId()));
        if (cause.isValid()) {
            builder.addLink(cause);
        }
        Span span = builder.startSpan();
        try (Scope ignored = span.makeCurrent()) {
            broadcaster.deliver(event);
        } finally {
            span.end();
        }
        return true;
    }
}
