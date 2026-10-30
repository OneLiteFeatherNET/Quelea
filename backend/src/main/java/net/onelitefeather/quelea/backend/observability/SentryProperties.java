package net.onelitefeather.quelea.backend.observability;

import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.NonNull;

/**
 * Error reporting to Sentry ({@code quelea.sentry}).
 *
 * @param dsn         the project's DSN; blank switches reporting off
 * @param environment the environment tag of every event, {@code production} unless configured otherwise
 */
@ConfigurationProperties("quelea.sentry")
public record SentryProperties(
        @NonNull String dsn,
        @NonNull String environment
) {

    @ConfigurationInject
    public SentryProperties {
    }
}
