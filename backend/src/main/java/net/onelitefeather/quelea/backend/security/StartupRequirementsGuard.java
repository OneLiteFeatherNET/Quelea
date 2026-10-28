package net.onelitefeather.quelea.backend.security;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.Environment;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Refuses to start outside the {@code dev} and {@code test} environments unless authentication is enabled, the
 * issuer is an https address, the audience is the API app registration's client id (a GUID, as Entra v2 puts it
 * in {@code aud}) and a tenant id is configured. The local defaults never match a real token, and without a
 * tenant id a multi-tenant authority would be trusted.
 */
@Context
public class StartupRequirementsGuard {

    private static final Set<String> LOCAL_ENVIRONMENTS = Set.of(Environment.DEVELOPMENT, Environment.TEST);
    private static final Pattern GUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    public StartupRequirementsGuard(
            Environment environment,
            QueleaAuthProperties properties,
            @Value("${micronaut.security.enabled:true}") boolean securityEnabled,
            @Value("${micronaut.security.token.jwt.claims-validators.issuer:}") String issuer,
            @Value("${micronaut.security.token.jwt.claims-validators.audience:}") String audience
    ) {
        check(securityEnabled, issuer, audience, properties.tenantId(), environment.getActiveNames());
    }

    /**
     * @throws IllegalStateException when no local environment is active and security is disabled, the issuer is
     *                               not https, the audience is not a GUID or the tenant id is empty
     */
    static void check(boolean securityEnabled, String issuer, String audience, String tenantId,
                      Set<String> activeEnvironments) {
        if (activeEnvironments.stream().anyMatch(LOCAL_ENVIRONMENTS::contains)) {
            return;
        }
        if (!securityEnabled) {
            throw new IllegalStateException("Authentication is disabled (micronaut.security.enabled=false). It is only "
                    + "allowed in the dev and test environments.");
        }
        if (isInsecureIssuer(issuer)) {
            throw new IllegalStateException("The token issuer '" + issuer + "' is a plain http or localhost address, "
                    + "which is only allowed in the dev and test environments. Set QUELEA_AUTH_ISSUER to the https "
                    + "issuer of your identity provider.");
        }
        if (audience == null || !GUID.matcher(audience.trim()).matches()) {
            throw new IllegalStateException("The token audience '" + audience + "' is not the API app client id. "
                    + "Outside the dev and test environments QUELEA_AUTH_AUDIENCE must be the GUID of the API "
                    + "app registration (not an api:// URI and not the local placeholder).");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("No tenant id is configured. Outside the dev and test environments "
                    + "QUELEA_AUTH_TENANT_ID must be set to the Entra tenant (directory) id.");
        }
    }

    static boolean isInsecureIssuer(String issuer) {
        if (issuer == null || issuer.isBlank()) {
            return true;
        }
        try {
            URI uri = URI.create(issuer.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            return !"https".equalsIgnoreCase(uri.getScheme())
                    || host.equals("localhost") || host.endsWith(".localhost")
                    || host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("::1");
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}
