package net.onelitefeather.quelea.velocity.error;

import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.User;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorReportersTest {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ErrorReportersTest.class);

    /** Records the options the SDK would be started with; no Sentry client is created. */
    private final List<SentryOptions> started = new ArrayList<>();

    @Test
    void aBlankDsnChoosesTheNoopReporterAndDoesNotStartSentry() {
        ErrorReporter reporter = ErrorReporters.start("   ", "production", "quelea@1.0.0", "proxy-1", started::add,
                LOGGER);

        assertInstanceOf(NoopErrorReporter.class, reporter);
        assertTrue(started.isEmpty(), "Sentry is not initialised without a DSN");
    }

    @Test
    void aMissingDsnChoosesTheNoopReporterAndDoesNotStartSentry() {
        ErrorReporter reporter = ErrorReporters.start(null, "production", "quelea@1.0.0", "proxy-1", started::add,
                LOGGER);

        assertInstanceOf(NoopErrorReporter.class, reporter);
        assertTrue(started.isEmpty(), "Sentry is not initialised without a DSN");
    }

    @Test
    void aDsnStartsSentryWithTheReleaseEnvironmentAndNoPersonalData() {
        ErrorReporter reporter = ErrorReporters.start("https://key@sentry.example/1", "staging", "quelea@1.2.3",
                "proxy-1", started::add, LOGGER);

        assertInstanceOf(SentryErrorReporter.class, reporter);
        assertEquals(1, started.size(), "Sentry is started once");
        SentryOptions options = started.get(0);
        assertEquals("https://key@sentry.example/1", options.getDsn());
        assertEquals("staging", options.getEnvironment());
        assertEquals("quelea@1.2.3", options.getRelease());
        assertEquals("proxy-1", options.getServerName());
        assertFalse(options.isSendDefaultPii(), "no personal data is sent");
        assertNull(options.getTracesSampleRate(), "no performance data is sent");
    }

    @Test
    void aFailingStartFallsBackToTheNoopReporter() {
        ErrorReporter reporter = ErrorReporters.start("https://key@sentry.example/1", "production", "quelea@1.2.3",
                "proxy-1", options -> {
                    throw new IllegalStateException("no network");
                }, LOGGER);

        assertInstanceOf(NoopErrorReporter.class, reporter);
    }

    @Test
    void theJvmWideHooksAreOffBecauseTheProxyJvmIsShared() {
        SentryOptions options = SentryErrorReporter.options("https://key@sentry.example/1", "production", "quelea@1",
                "proxy-1");

        assertFalse(options.isEnableUncaughtExceptionHandler(), "other plugins' crashes are not captured");
        assertFalse(options.isEnableShutdownHook(), "the plugin closes Sentry on ProxyShutdownEvent");
        assertFalse(options.isEnableAutoSessionTracking(), "no JVM-wide session data");
        assertFalse(options.isEnableExternalConfiguration(), "no configuration from outside the plugin");
    }

    @Test
    void scrubMasksUuidsInTheMessageItsParamsAndTheExceptionValues() {
        String uuid = "00000000-0000-0000-0000-00000000000A";
        SentryEvent event = new SentryEvent();
        Message message = new Message();
        message.setMessage("player " + uuid + " failed");
        message.setFormatted("player " + uuid + " failed");
        message.setParams(List.of(uuid));
        event.setMessage(message);
        SentryException exception = new SentryException();
        exception.setValue("party " + uuid + " is full");
        event.setExceptions(List.of(exception));

        SentryEvent scrubbed = SentryErrorReporter.scrub(event);

        assertEquals("player {id} failed", scrubbed.getMessage().getMessage());
        assertEquals("player {id} failed", scrubbed.getMessage().getFormatted());
        assertEquals(List.of("{id}"), scrubbed.getMessage().getParams());
        assertEquals("party {id} is full", scrubbed.getExceptions().get(0).getValue());
    }

    @Test
    void scrubDropsRequestHeadersCookiesAndTheUserIpAddress() {
        SentryEvent event = new SentryEvent();
        Request request = new Request();
        request.setHeaders(Map.of("Authorization", "Bearer secret"));
        request.setCookies("session=1");
        event.setRequest(request);
        User user = new User();
        user.setIpAddress("203.0.113.7");
        event.setUser(user);

        SentryEvent scrubbed = SentryErrorReporter.scrub(event);

        assertNull(scrubbed.getRequest().getHeaders(), "headers are dropped");
        assertNull(scrubbed.getRequest().getCookies(), "cookies are dropped");
        assertNull(scrubbed.getUser().getIpAddress(), "the IP address is dropped");
    }
}
