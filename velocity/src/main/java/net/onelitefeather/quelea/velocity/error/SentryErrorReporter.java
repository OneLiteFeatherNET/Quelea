package net.onelitefeather.quelea.velocity.error;

import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.User;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Sends the reports to Sentry. The SDK is started by {@link ErrorReporters#start}; this class only reports into it.
 */
public final class SentryErrorReporter implements ErrorReporter {

    static final String ID_MASK = "{id}";
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", Pattern.CASE_INSENSITIVE);

    /**
     * Options for the plugin's Sentry project. No performance data is sent (no sample rate), no personal data is
     * sent by default, and {@link #scrub} removes what the SDK may still attach. The proxy JVM is shared with other
     * plugins, so every JVM-wide hook of the SDK is off: the uncaught-exception handler (it would capture other
     * plugins' crashes), the shutdown hook (the plugin closes Sentry on ProxyShutdownEvent), session tracking,
     * external configuration, thread and module lists, and OpenTelemetry events.
     *
     * @param release the release name, {@code quelea@<version>}
     * @return the options to start the SDK with
     */
    public static SentryOptions options(String dsn, String environment, String release, String proxyId) {
        SentryOptions options = new SentryOptions();
        options.setDsn(dsn);
        options.setEnvironment(environment);
        options.setRelease(release);
        options.setServerName(proxyId);
        options.setSendDefaultPii(false);
        options.setEnableUncaughtExceptionHandler(false);
        options.setEnableShutdownHook(false);
        options.setEnableAutoSessionTracking(false);
        options.setEnableExternalConfiguration(false);
        options.setAttachThreads(false);
        options.setSendModules(false);
        options.setCaptureOpenTelemetryEvents(false);
        options.setBeforeSend((event, hint) -> scrub(event));
        return options;
    }

    /**
     * Drops request headers, cookies and the user's IP address from an event, and masks every UUID (player and party
     * ids in exception messages) in the message, its parameters and the exception values. Tags are not touched.
     */
    static SentryEvent scrub(SentryEvent event) {
        Request request = event.getRequest();
        if (request != null) {
            request.setHeaders(null);
            request.setCookies(null);
        }
        User user = event.getUser();
        if (user != null) {
            user.setIpAddress(null);
        }
        maskMessage(event.getMessage());
        List<SentryException> exceptions = event.getExceptions();
        if (exceptions != null) {
            exceptions.forEach(exception -> exception.setValue(maskIds(exception.getValue())));
        }
        return event;
    }

    /** Replaces every UUID in {@code text} with {@value #ID_MASK}; null stays null. */
    static String maskIds(String text) {
        return text == null ? null : UUID.matcher(text).replaceAll(ID_MASK);
    }

    private static void maskMessage(Message message) {
        if (message == null) {
            return;
        }
        message.setMessage(maskIds(message.getMessage()));
        message.setFormatted(maskIds(message.getFormatted()));
        if (message.getParams() != null) {
            message.setParams(message.getParams().stream().map(SentryErrorReporter::maskIds).toList());
        }
    }

    @Override
    public void report(Throwable error, Map<String, String> tags) {
        Sentry.captureException(error, scope -> tags.forEach(scope::setTag));
    }

    @Override
    public void report(String message, Map<String, String> tags) {
        Sentry.captureMessage(message, scope -> tags.forEach(scope::setTag));
    }

    @Override
    public void close() {
        Sentry.close();
    }
}
