package net.onelitefeather.quelea.backend.security;

import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.NonNull;

import java.time.Duration;

/**
 * Token settings ({@code quelea.auth}).
 *
 * @param tenantId  the Entra tenant; when set, tokens of another tenant ({@code tid}) are refused
 * @param jwksUrl   where the signing keys of the issuer are published
 * @param clockSkew tolerated difference between the issuer's clock and this one for {@code exp} and {@code nbf}
 */
@ConfigurationProperties("quelea.auth")
public record QueleaAuthProperties(
        @NonNull String tenantId,
        @NonNull String jwksUrl,
        @NonNull Duration clockSkew
) {

    @ConfigurationInject
    public QueleaAuthProperties {
    }
}
