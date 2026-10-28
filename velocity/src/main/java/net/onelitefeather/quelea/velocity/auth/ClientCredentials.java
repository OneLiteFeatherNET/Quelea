package net.onelitefeather.quelea.velocity.auth;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The app registration the plugin signs in with (OAuth 2.0 client credentials). Values come from
 * {@code config.properties} or the environment, see {@code QueleaConfig}. Blank values count as missing.
 *
 * @param tenantId     the Entra tenant (a GUID or a domain)
 * @param clientId     the client id of the plugin's app registration
 * @param clientSecret a secret of that app registration
 * @param scope        what the token is for, e.g. {@code api://<client id of the API>/.default}
 */
public record ClientCredentials(String tenantId, String clientId, String clientSecret, String scope) {

    private static final Pattern TENANT = Pattern.compile("[A-Za-z0-9.-]+");

    public ClientCredentials {
        tenantId = clean(tenantId);
        clientId = clean(clientId);
        clientSecret = clean(clientSecret);
        scope = clean(scope);
    }

    /**
     * @return the config keys of the settings still missing, empty when a token can be requested
     */
    public List<String> missing() {
        List<String> missing = new ArrayList<>();
        if (tenantId.isEmpty()) {
            missing.add("tenant-id");
        }
        if (clientId.isEmpty()) {
            missing.add("client-id");
        }
        if (clientSecret.isEmpty()) {
            missing.add("client-secret");
        }
        if (scope.isEmpty()) {
            missing.add("auth-scope");
        }
        return missing;
    }

    public boolean isComplete() {
        return missing().isEmpty();
    }

    /**
     * @return Entra's token endpoint of the tenant
     * @throws IllegalArgumentException if the tenant is not a plain id or domain
     */
    public URI tokenUri() {
        if (!TENANT.matcher(tenantId).matches()) {
            throw new IllegalArgumentException("Invalid tenant-id: expected a tenant GUID or domain");
        }
        return URI.create("https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token");
    }

    /** Never prints the secret. */
    @Override
    public String toString() {
        return "ClientCredentials{tenantId='" + tenantId + "', clientId='" + clientId + "', clientSecret="
                + (clientSecret.isEmpty() ? "<unset>" : "<redacted>") + ", scope='" + scope + "'}";
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
