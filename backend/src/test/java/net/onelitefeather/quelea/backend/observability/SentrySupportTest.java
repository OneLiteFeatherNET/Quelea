package net.onelitefeather.quelea.backend.observability;

import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.protocol.SentryException;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SentrySupportTest {

    private static final String DSN = "https://public@o0.ingest.sentry.io/1";
    private static final String VERSION = "0.3.0";
    private static final String PLAYER_ID = "3f2c9a1e-7b4d-4c8e-9a10-5d6e7f8a9b0c";

    @Test
    void emptyDsnDisablesReporting() {
        assertFalse(SentrySupport.isReportingEnabled(properties("")));
    }

    @Test
    void whitespaceDsnDisablesReporting() {
        assertFalse(SentrySupport.isReportingEnabled(properties("   ")));
    }

    @Test
    void nonBlankDsnEnablesReporting() {
        assertTrue(SentrySupport.isReportingEnabled(properties(DSN)));
    }

    @Test
    void personalDataIsNotSent() {
        SentryOptions options = configured(properties(DSN));

        assertFalse(options.isSendDefaultPii(), "sendDefaultPii must be off");
    }

    @Test
    void releaseCarriesTheApplicationVersion() {
        SentryOptions options = configured(properties(DSN));

        assertEquals("quelea@0.3.0", options.getRelease());
    }

    @Test
    void environmentComesFromConfiguration() {
        SentryOptions options = configured(new SentryProperties(DSN, "staging"));

        assertEquals("staging", options.getEnvironment());
    }

    @Test
    void eventsCarryComponentBackendTag() {
        SentryOptions options = configured(properties(DSN));

        assertEquals("backend", options.getTags().get("component"));
    }

    @Test
    void performanceTracingStaysOff() {
        SentryOptions options = configured(properties(DSN));

        assertNull(options.getTracesSampleRate(), "no traces sample rate: performance data stays in Tempo");
    }

    @Test
    void traceIdMdcKeyBecomesTraceIdTag() {
        SentryOptions options = configured(properties(DSN));

        assertTrue(options.getContextTags().contains("trace_id"), "the MDC key trace_id must be copied to a tag");
    }

    @Test
    void serverNameIsTheHostname() {
        SentryOptions options = new SentryOptions();

        SentrySupport.configure(options, properties(DSN), VERSION, "quelea-7f9c");

        assertEquals("quelea-7f9c", options.getServerName());
    }

    @Test
    void beforeSendRemovesRequestHeaders() {
        SentryEvent event = eventWithRequestAndUser();

        SentryEvent scrubbed = SentrySupport.scrub(event);

        assertNull(scrubbed.getRequest().getHeaders(), "request headers must be removed");
    }

    @Test
    void beforeSendRemovesRequestCookies() {
        SentryEvent event = eventWithRequestAndUser();

        SentryEvent scrubbed = SentrySupport.scrub(event);

        assertNull(scrubbed.getRequest().getCookies(), "request cookies must be removed");
    }

    @Test
    void beforeSendRemovesUserIpAddress() {
        SentryEvent event = eventWithRequestAndUser();

        SentryEvent scrubbed = SentrySupport.scrub(event);

        assertNull(scrubbed.getUser().getIpAddress(), "the user IP address must be removed");
    }

    @Test
    void beforeSendKeepsTheEventItself() {
        SentryEvent event = eventWithRequestAndUser();

        assertSame(event, SentrySupport.scrub(event), "scrubbing returns the same event, not a copy");
    }

    @Test
    void beforeSendToleratesEventsWithoutRequestOrUser() {
        SentryEvent event = new SentryEvent();

        assertSame(event, SentrySupport.scrub(event));
    }

    @Test
    void configuredBeforeSendScrubsTheEvent() {
        SentryOptions options = configured(properties(DSN));
        SentryEvent event = eventWithRequestAndUser();

        SentryEvent sent = options.getBeforeSend().execute(event, new Hint());

        assertNull(sent.getRequest().getCookies(), "the configured beforeSend must scrub cookies");
    }

    @Test
    void applicationVersionIsReadFromTheBuild() {
        assertNotEquals("unknown", SentrySupport.applicationVersion(), "the build must provide quelea-build.properties");
    }

    @Test
    void accessLogBreadcrumbIsDropped() {
        Breadcrumb breadcrumb = breadcrumb("HTTP_ACCESS_LOGGER", "GET /v1/party 200");

        assertNull(SentrySupport.filterBreadcrumb(breadcrumb), "access log lines must not become breadcrumbs");
    }

    @Test
    void otherBreadcrumbIsKept() {
        Breadcrumb breadcrumb = breadcrumb("net.onelitefeather.quelea.backend.event.EventFanout", "Dropping message");

        assertSame(breadcrumb, SentrySupport.filterBreadcrumb(breadcrumb));
    }

    @Test
    void uuidInBreadcrumbMessageIsMasked() {
        Breadcrumb breadcrumb = breadcrumb("net.onelitefeather.quelea.backend.api.PartyController",
                "joined party " + PLAYER_ID);

        assertEquals("joined party {id}", SentrySupport.filterBreadcrumb(breadcrumb).getMessage());
    }

    @Test
    void uuidInMessageIsMasked() {
        SentryEvent event = new SentryEvent();
        Message message = new Message();
        message.setMessage("Unhandled exception for {} {}");
        message.setFormatted("Unhandled exception for GET /v1/players/" + PLAYER_ID + "/party");
        message.setParams(List.of("GET", "/v1/players/" + PLAYER_ID + "/party"));
        event.setMessage(message);

        SentrySupport.scrub(event);

        assertEquals("Unhandled exception for GET /v1/players/{id}/party", message.getFormatted());
        assertEquals(List.of("GET", "/v1/players/{id}/party"), message.getParams());
    }

    @Test
    void uuidInMessageTemplateIsMasked() {
        SentryEvent event = new SentryEvent();
        Message message = new Message();
        message.setMessage("Player " + PLAYER_ID + " failed");
        event.setMessage(message);

        SentrySupport.scrub(event);

        assertEquals("Player {id} failed", message.getMessage());
    }

    @Test
    void uuidInExceptionValueIsMasked() {
        SentryEvent event = new SentryEvent();
        SentryException exception = new SentryException();
        exception.setValue("no party for " + PLAYER_ID);
        event.setExceptions(List.of(exception));

        SentrySupport.scrub(event);

        assertEquals("no party for {id}", exception.getValue());
    }

    @Test
    void uppercaseUuidIsMasked() {
        assertEquals("party {id}", SentrySupport.maskIds("party " + PLAYER_ID.toUpperCase(Locale.ROOT)));
    }

    @Test
    void textWithoutUuidIsUnchanged() {
        assertEquals("party not found", SentrySupport.maskIds("party not found"));
    }

    @Test
    void nullTextStaysNull() {
        assertNull(SentrySupport.maskIds(null));
    }

    @Test
    void configuredBeforeBreadcrumbDropsAccessLog() {
        SentryOptions options = configured(properties(DSN));

        Breadcrumb breadcrumb = breadcrumb("HTTP_ACCESS_LOGGER", "GET /health 200");

        assertNull(options.getBeforeBreadcrumb().execute(breadcrumb, new Hint()));
    }

    private static SentryProperties properties(String dsn) {
        return new SentryProperties(dsn, "production");
    }

    private static SentryOptions configured(SentryProperties properties) {
        SentryOptions options = new SentryOptions();
        SentrySupport.configure(options, properties, VERSION, "local");
        return options;
    }

    private static Breadcrumb breadcrumb(String category, String message) {
        Breadcrumb breadcrumb = new Breadcrumb();
        breadcrumb.setCategory(category);
        breadcrumb.setMessage(message);
        return breadcrumb;
    }

    private static SentryEvent eventWithRequestAndUser() {
        SentryEvent event = new SentryEvent();
        Request request = new Request();
        request.setHeaders(Map.of("Authorization", "Bearer secret"));
        request.setCookies("session=abc");
        event.setRequest(request);
        User user = new User();
        user.setIpAddress("203.0.113.7");
        event.setUser(user);
        return event;
    }
}
