package net.onelitefeather.quelea.velocity.error;

import java.util.Map;

/**
 * Where the plugin sends the errors it reports. Reporting is best effort: an implementation must never throw into
 * the caller, and tags must never carry player names, player ids or request headers.
 */
public interface ErrorReporter {

    void report(Throwable error, Map<String, String> tags);

    void report(String message, Map<String, String> tags);

    /**
     * Flushes and releases the reporter. Called once when the proxy shuts down.
     */
    default void close() {
    }
}
