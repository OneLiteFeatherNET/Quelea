package net.onelitefeather.quelea.backend.event;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;

/** Reads the current OpenTelemetry span context; both values are {@code null} when no valid span is active. */
public final class TraceContext {

    private TraceContext() {
    }

    /** The W3C {@code traceparent} of the current span ({@code 00-<trace>-<span>-<flags>}), or {@code null}. */
    public static String traceparent() {
        SpanContext context = Span.current().getSpanContext();
        if (!context.isValid()) {
            return null;
        }
        return "00-" + context.getTraceId() + "-" + context.getSpanId() + "-" + context.getTraceFlags().asHex();
    }

    /** The trace id of the current span, or {@code null}. */
    public static String traceId() {
        SpanContext context = Span.current().getSpanContext();
        return context.isValid() ? context.getTraceId() : null;
    }
}
