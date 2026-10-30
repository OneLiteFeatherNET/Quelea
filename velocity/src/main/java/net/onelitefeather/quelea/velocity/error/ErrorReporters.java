package net.onelitefeather.quelea.velocity.error;

import io.sentry.SentryOptions;
import org.slf4j.Logger;

import java.util.function.Consumer;

/**
 * Chooses the reporter at startup. Sentry is started only for a non-blank DSN; otherwise nothing is initialised.
 */
public final class ErrorReporters {

    private ErrorReporters() {
    }

    /**
     * @param dsn     the Sentry DSN; blank or {@code null} switches reporting off
     * @param starter starts the Sentry SDK with the options; production passes {@code options -> Sentry.init(options)},
     *                tests a recorder
     * @param logger  receives a warning if the SDK cannot be started; the DSN is never logged
     * @return a {@link SentryErrorReporter} when the SDK was started, otherwise a {@link NoopErrorReporter}
     */
    public static ErrorReporter start(String dsn, String environment, String release, String proxyId,
                                      Consumer<SentryOptions> starter, Logger logger) {
        if (dsn == null || dsn.isBlank()) {
            return new NoopErrorReporter();
        }
        try {
            starter.accept(SentryErrorReporter.options(dsn, environment, release, proxyId));
            return new SentryErrorReporter();
        } catch (RuntimeException e) {
            logger.warn("Sentry could not be started, errors are not reported: {}", e.getMessage());
            return new NoopErrorReporter();
        }
    }
}
