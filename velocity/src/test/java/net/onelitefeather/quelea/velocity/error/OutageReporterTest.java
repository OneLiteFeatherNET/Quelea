package net.onelitefeather.quelea.velocity.error;

import net.onelitefeather.quelea.velocity.auth.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutageReporterTest {

    private final MutableClock clock = new MutableClock();
    private final FakeErrorReporter reporter = new FakeErrorReporter();
    private final OutageReporter outage = new OutageReporter(reporter, clock, OutageReporter.THRESHOLD, "proxy-1");

    @Test
    void anOutageShorterThanFiveMinutesIsNotReported() {
        outage.disconnected();
        clock.advance(Duration.ofMinutes(4).plusSeconds(59));
        outage.disconnected();

        assertTrue(reporter.reports().isEmpty(), "no report before the threshold");
    }

    @Test
    void anOutageThatReachesFiveMinutesIsReportedOnce() {
        outage.disconnected();
        clock.advance(Duration.ofMinutes(5));
        outage.disconnected();

        assertEquals(1, reporter.reports().size(), "exactly one report for the outage");
    }

    @Test
    void retriesDuringTheSameOutageAreNotReportedAgain() {
        outage.disconnected();
        clock.advance(Duration.ofMinutes(5));
        outage.disconnected();
        clock.advance(Duration.ofSeconds(30));
        outage.disconnected();
        clock.advance(Duration.ofSeconds(30));
        outage.disconnected();

        assertEquals(1, reporter.reports().size(), "failed reconnects of the same outage add no report");
    }

    @Test
    void aSecondOutageIsReportedOnlyAfterAReconnect() {
        outage.disconnected();
        clock.advance(Duration.ofMinutes(5));
        outage.disconnected();
        outage.connected();
        outage.disconnected();
        clock.advance(Duration.ofMinutes(5));
        outage.disconnected();

        assertEquals(2, reporter.reports().size(), "the reconnect re-arms the report");
    }

    @Test
    void aReconnectBeforeTheThresholdStartsTheCountAgain() {
        outage.disconnected();
        clock.advance(Duration.ofMinutes(4));
        outage.connected();
        outage.disconnected();
        clock.advance(Duration.ofMinutes(4));
        outage.disconnected();

        assertTrue(reporter.reports().isEmpty(), "each outage counts from its own first disconnect");
    }

    @Test
    void theReportIsTaggedWithTheComponentAndTheProxyOnly() {
        outage.disconnected();
        clock.advance(Duration.ofMinutes(5));
        outage.disconnected();

        assertEquals(Map.of("component", "velocity", "proxy_id", "proxy-1"),
                reporter.reports().get(0).tags());
    }
}
