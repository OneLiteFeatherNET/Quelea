package net.onelitefeather.quelea.velocity.error;

import net.onelitefeather.quelea.velocity.http.QueleaProblemException;
import net.onelitefeather.quelea.velocity.http.QueleaUnavailableException;
import net.onelitefeather.quelea.velocity.i18n.Messages;

import java.util.Locale;
import java.util.Set;

/**
 * Decides which failures of the plugin reach Sentry. Callers pass every caught failure here; the decision is kept
 * apart from the Sentry SDK so it can be tested with a fake {@link ErrorReporter}.
 *
 * <p>Reported: unexpected exceptions, HTTP answers with a status of 5xx or without a problem document, 401 and 403
 * problems (broken credentials), and 4xx problems whose code has no entry in the plugin's messages. Not reported: a
 * documented domain problem (a 4xx whose code the plugin maps to a message, such as {@code party-full}) and a network
 * failure, because that is either a normal outcome or recovered by the retry.
 */
public final class FailureReports {

    private static final Set<String> CREDENTIAL_CODES = Set.of("unauthorized", "forbidden");

    private final ErrorReporter reporter;
    private final Messages messages;
    private final String proxyId;

    public FailureReports(ErrorReporter reporter, Messages messages, String proxyId) {
        this.reporter = reporter;
        this.messages = messages;
        this.proxyId = proxyId;
    }

    /**
     * @param error   the caught failure
     * @param partyId the party the failure concerns, or {@code null} when it is not known
     */
    public void failure(RuntimeException error, String partyId) {
        if (shouldReport(error)) {
            reporter.report(error, ErrorTags.of(proxyId, partyId));
        }
    }

    boolean shouldReport(RuntimeException error) {
        if (error instanceof QueleaProblemException problem) {
            return isUndocumented(problem);
        }
        if (error instanceof QueleaUnavailableException unavailable) {
            // Only an answer with an HTTP status counts; a refused connection is a network error, not a report.
            return unavailable.httpStatus().isPresent();
        }
        return true;
    }

    private boolean isUndocumented(QueleaProblemException problem) {
        if (problem.status() >= 500 || CREDENTIAL_CODES.contains(problem.code())) {
            return true;
        }
        return !messages.hasKey(Locale.ENGLISH, "problem." + problem.code());
    }
}
