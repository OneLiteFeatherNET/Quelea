package net.onelitefeather.quelea.velocity.error;

import java.util.Map;

/**
 * Used when no DSN is configured: nothing is sent and nothing is contacted.
 */
public final class NoopErrorReporter implements ErrorReporter {

    @Override
    public void report(Throwable error, Map<String, String> tags) {
        // Reporting is off.
    }

    @Override
    public void report(String message, Map<String, String> tags) {
        // Reporting is off.
    }
}
