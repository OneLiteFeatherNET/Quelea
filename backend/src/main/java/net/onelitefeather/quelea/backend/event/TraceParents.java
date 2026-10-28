package net.onelitefeather.quelea.backend.event;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;

import java.util.Map;

/** Parses the W3C {@code traceparent} carried by an event back into a remote span context. */
public final class TraceParents {

    private static final String HEADER = "traceparent";

    private static final TextMapGetter<Map<String, String>> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };

    private TraceParents() {
    }

    /** The remote span context of {@code traceparent}, or {@link SpanContext#getInvalid()} if it does not parse. */
    public static SpanContext parse(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return SpanContext.getInvalid();
        }
        Context extracted = W3CTraceContextPropagator.getInstance()
                .extract(Context.root(), Map.of(HEADER, traceparent), GETTER);
        return Span.fromContext(extracted).getSpanContext();
    }
}
