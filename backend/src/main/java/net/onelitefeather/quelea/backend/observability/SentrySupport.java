package net.onelitefeather.quelea.backend.observability;

import io.sentry.Breadcrumb;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.Request;
import io.sentry.protocol.User;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Pure helpers for Sentry error reporting. Nothing here opens a connection, so the options can be built and
 * scrubbed in unit tests.
 */
public final class SentrySupport {

    static final String COMPONENT_TAG = "component";
    static final String COMPONENT = "backend";
    /** MDC key the OpenTelemetry appender fills with the current trace id (see logback.xml). */
    static final String TRACE_ID_MDC_KEY = "trace_id";
    static final String RELEASE_PREFIX = "quelea@";
    static final String UNKNOWN_VERSION = "unknown";
    static final String BUILD_PROPERTIES = "/quelea-build.properties";
    /** Micronaut's access log: one line per request, heartbeats included. Noise for a breadcrumb trail. */
    static final String HTTP_ACCESS_LOGGER = "HTTP_ACCESS_LOGGER";
    static final String ID_MASK = "{id}";
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", Pattern.CASE_INSENSITIVE);

    private SentrySupport() {
    }

    /** Reporting is on only for a non-blank DSN. */
    public static boolean isReportingEnabled(SentryProperties properties) {
        return properties.dsn() != null && !properties.dsn().isBlank();
    }

    /**
     * Sets every option of the backend. No traces sample rate is set, so performance data stays off, and personal
     * data is not sent.
     */
    public static void configure(SentryOptions options, SentryProperties properties, String version, String hostname) {
        options.setDsn(properties.dsn().trim());
        options.setRelease(RELEASE_PREFIX + version);
        options.setEnvironment(properties.environment());
        options.setServerName(hostname);
        options.setSendDefaultPii(false);
        options.setTag(COMPONENT_TAG, COMPONENT);
        options.addContextTag(TRACE_ID_MDC_KEY);
        options.setBeforeSend((event, hint) -> scrub(event));
        options.setBeforeBreadcrumb((breadcrumb, hint) -> filterBreadcrumb(breadcrumb));
    }

    /**
     * Removes request headers, cookies and the user IP address, as defence in depth, and masks every UUID (player
     * and party ids in paths and messages) in the message, its parameters, exception values and breadcrumbs.
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
        List<Breadcrumb> breadcrumbs = event.getBreadcrumbs();
        if (breadcrumbs != null) {
            breadcrumbs.forEach(breadcrumb -> breadcrumb.setMessage(maskIds(breadcrumb.getMessage())));
        }
        return event;
    }

    /** Drops the access log lines, which would push the useful breadcrumbs out; masks ids in the others. */
    static Breadcrumb filterBreadcrumb(Breadcrumb breadcrumb) {
        if (HTTP_ACCESS_LOGGER.equals(breadcrumb.getCategory())) {
            return null;
        }
        breadcrumb.setMessage(maskIds(breadcrumb.getMessage()));
        return breadcrumb;
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
            message.setParams(message.getParams().stream().map(SentrySupport::maskIds).toList());
        }
    }

    /** The application version from the build, or {@value #UNKNOWN_VERSION} when the resource is missing. */
    public static String applicationVersion() {
        try (InputStream in = SentrySupport.class.getResourceAsStream(BUILD_PROPERTIES)) {
            if (in == null) {
                return UNKNOWN_VERSION;
            }
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version", "").trim();
            return version.isEmpty() ? UNKNOWN_VERSION : version;
        } catch (IOException e) {
            return UNKNOWN_VERSION;
        }
    }

    /** The pod name in Kubernetes; {@code local} when HOSTNAME is not set. */
    public static String hostname() {
        String hostname = System.getenv("HOSTNAME");
        return hostname == null || hostname.isBlank() ? "local" : hostname.trim();
    }
}
