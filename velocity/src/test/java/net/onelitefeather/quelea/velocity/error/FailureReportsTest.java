package net.onelitefeather.quelea.velocity.error;

import net.onelitefeather.quelea.velocity.http.QueleaProblemException;
import net.onelitefeather.quelea.velocity.http.QueleaUnavailableException;
import net.onelitefeather.quelea.velocity.i18n.Messages;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailureReportsTest {

    private final FakeErrorReporter reporter = new FakeErrorReporter();
    private final FailureReports failures = new FailureReports(reporter,
            Messages.load(null, LoggerFactory.getLogger(FailureReportsTest.class)), "proxy-1");

    @Test
    void aProblemCodeWithoutAMessageIsReported() {
        failures.failure(new QueleaProblemException("brand-new-code", 409, null), null);

        assertEquals(1, reporter.reports().size(), "an undocumented answer is reported");
    }

    @Test
    void a503AnswerWithAProblemDocumentIsReported() {
        failures.failure(new QueleaProblemException("lock-timeout", 503, null), null);

        assertEquals(1, reporter.reports().size(), "a 5xx is reported even when it is documented");
    }

    @Test
    void anAnswerWithoutAProblemDocumentIsReportedWithItsStatus() {
        failures.failure(new QueleaUnavailableException("party service answered HTTP 502", 502), null);

        assertEquals(1, reporter.reports().size(), "a 502 without a problem document is reported");
    }

    @Test
    void theKnownPartyIsTagged() {
        failures.failure(new IllegalStateException("boom"), "p-1");

        assertEquals("p-1", reporter.reports().get(0).tags().get("party_id"));
        assertTrue(reporter.reports().get(0).tags().containsKey("component"));
    }
}
