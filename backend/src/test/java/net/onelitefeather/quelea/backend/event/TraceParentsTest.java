package net.onelitefeather.quelea.backend.event;

import io.opentelemetry.api.trace.SpanContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceParentsTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID = "00f067aa0ba902b7";

    @Test
    void validTraceparentYieldsRemoteContextWithItsTraceId() {
        SpanContext context = TraceParents.parse("00-" + TRACE_ID + "-" + SPAN_ID + "-01");

        assertTrue(context.isValid(), "a well-formed traceparent must parse");
        assertEquals(TRACE_ID, context.getTraceId(), "the trace id must be the one in the header");
        assertEquals(SPAN_ID, context.getSpanId(), "the span id must be the one in the header");
        assertTrue(context.isSampled(), "flag 01 means sampled");
        assertTrue(context.isRemote(), "the context came from another process and must be marked remote");
    }

    @Test
    void garbageYieldsInvalidContext() {
        assertFalse(TraceParents.parse("not-a-traceparent").isValid(), "garbage must not produce a valid context");
    }

    @Test
    void wrongVersionOrZeroTraceIdYieldsInvalidContext() {
        assertFalse(TraceParents.parse("00-00000000000000000000000000000000-" + SPAN_ID + "-01").isValid(),
                "an all-zero trace id is invalid by the W3C spec");
    }

    @Test
    void missingTraceparentYieldsInvalidContext() {
        assertFalse(TraceParents.parse(null).isValid(), "an event without traceparent must not fail the fan-out");
        assertFalse(TraceParents.parse("  ").isValid(), "a blank traceparent is treated as absent");
    }
}
