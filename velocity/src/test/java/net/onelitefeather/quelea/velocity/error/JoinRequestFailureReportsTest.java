package net.onelitefeather.quelea.velocity.error;

import net.onelitefeather.quelea.velocity.http.QueleaProblemException;
import net.onelitefeather.quelea.velocity.i18n.Messages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The six join-request codes are documented domain answers and must never reach Sentry. */
class JoinRequestFailureReportsTest {

    @TempDir
    Path overrides;

    @Test
    void noJoinRequestProblemIsReported() {
        FakeErrorReporter reporter = new FakeErrorReporter();
        FailureReports failures = new FailureReports(reporter,
                Messages.load(overrides, LoggerFactory.getLogger(JoinRequestFailureReportsTest.class)), "proxy-1");

        List<QueleaProblemException> answers = List.of(
                new QueleaProblemException("party-not-public", 409, null),
                new QueleaProblemException("party-banned", 403, null),
                new QueleaProblemException("target-not-in-party", 404, null),
                new QueleaProblemException("join-request-pending", 409, null),
                new QueleaProblemException("join-request-cooldown", 429, null, 60L),
                new QueleaProblemException("join-request-not-found", 404, null));
        for (QueleaProblemException answer : answers) {
            failures.failure(answer, null);
        }

        assertEquals(0, reporter.reports().size(), "documented join-request answers must not be reported to Sentry");
    }
}
