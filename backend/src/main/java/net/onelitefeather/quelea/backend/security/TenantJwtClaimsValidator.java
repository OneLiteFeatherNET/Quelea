package net.onelitefeather.quelea.backend.security;

import io.micronaut.context.env.Environment;
import io.micronaut.security.token.Claims;
import io.micronaut.security.token.jwt.validator.GenericJwtClaimsValidator;
import jakarta.inject.Singleton;

import java.util.Set;

/**
 * Refuses tokens of another Entra tenant when {@code quelea.auth.tenant-id} is set. With no tenant configured
 * it accepts all tokens, but only in the dev and test environments; elsewhere {@link StartupRequirementsGuard}
 * stops the start before that.
 */
@Singleton
public class TenantJwtClaimsValidator implements GenericJwtClaimsValidator<Object> {

    static final String TENANT_CLAIM = "tid";

    private final String tenantId;
    private final boolean tenantOptional;

    public TenantJwtClaimsValidator(QueleaAuthProperties properties, Environment environment) {
        this.tenantId = properties.tenantId().trim();
        this.tenantOptional = environment.getActiveNames().stream()
                .anyMatch(Set.of(Environment.DEVELOPMENT, Environment.TEST)::contains);
    }

    @Override
    public boolean validate(Claims claims, Object request) {
        if (this.tenantId.isEmpty()) {
            return this.tenantOptional;
        }
        return this.tenantId.equals(String.valueOf(claims.get(TENANT_CLAIM)));
    }
}
