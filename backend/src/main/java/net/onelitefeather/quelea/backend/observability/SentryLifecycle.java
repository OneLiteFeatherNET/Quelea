package net.onelitefeather.quelea.backend.observability;

import io.micronaut.context.annotation.Context;
import io.sentry.Sentry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Starts Sentry when a DSN is configured and closes it on shutdown. Without a DSN the SDK is never initialised by
 * this bean, so no client exists and no event is sent.
 */
@Context
public class SentryLifecycle {

    private final SentryProperties properties;

    public SentryLifecycle(SentryProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void start() {
        if (!SentrySupport.isReportingEnabled(properties)) {
            return;
        }
        String version = SentrySupport.applicationVersion();
        String hostname = SentrySupport.hostname();
        Sentry.init(options -> SentrySupport.configure(options, properties, version, hostname));
    }

    @PreDestroy
    void stop() {
        Sentry.close();
    }
}
