package net.onelitefeather.quelea.velocity.error;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Reports one event-stream outage: once the stream has stayed disconnected for the threshold. The next report comes
 * only after a successful reconnect. Fed by the stream's connect and disconnect callbacks, which run on the stream's
 * own thread, so no locking is needed. Failed reconnect attempts (with backoff) keep calling {@link #disconnected()},
 * which is what lets the threshold be reached without a timer.
 */
public final class OutageReporter {

    public static final Duration THRESHOLD = Duration.ofMinutes(5);

    private static final String MESSAGE = "Event stream disconnected for more than 5 minutes";

    private final ErrorReporter reporter;
    private final Clock clock;
    private final Duration threshold;
    private final String proxyId;

    private Instant since;
    private boolean reported;

    public OutageReporter(ErrorReporter reporter, Clock clock, Duration threshold, String proxyId) {
        this.reporter = reporter;
        this.clock = clock;
        this.threshold = threshold;
        this.proxyId = proxyId;
    }

    /** Called after every failed connect and every end of the stream. */
    public void disconnected() {
        Instant now = clock.instant();
        if (since == null) {
            since = now;
        }
        if (!reported && !now.isBefore(since.plus(threshold))) {
            reported = true;
            reporter.report(MESSAGE, ErrorTags.of(proxyId, null));
        }
    }

    /** Called after a successful connect: the next outage is a new one. */
    public void connected() {
        since = null;
        reported = false;
    }
}
