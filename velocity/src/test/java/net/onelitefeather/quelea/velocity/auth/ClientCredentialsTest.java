package net.onelitefeather.quelea.velocity.auth;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientCredentialsTest {

    private static final String SECRET = "very-secret-value";

    @Test
    void blankAndMissingValuesAreNamedByTheirConfigKey() {
        ClientCredentials empty = new ClientCredentials(null, " ", "", null);

        assertEquals(List.of("tenant-id", "client-id", "client-secret", "auth-scope"), empty.missing());
        assertFalse(empty.isComplete());
    }

    @Test
    void completeValuesNeedNothingMore() {
        ClientCredentials complete = new ClientCredentials("tenant-1", "client-1", SECRET, "api://api-1/.default");

        assertTrue(complete.missing().isEmpty());
        assertTrue(complete.isComplete());
        assertEquals(URI.create("https://login.microsoftonline.com/tenant-1/oauth2/v2.0/token"), complete.tokenUri());
    }

    @Test
    void toStringRedactsTheSecret() {
        ClientCredentials complete = new ClientCredentials("tenant-1", "client-1", SECRET, "api://api-1/.default");

        assertFalse(complete.toString().contains(SECRET), complete.toString());
        assertTrue(complete.toString().contains("<redacted>"), complete.toString());
    }

    @Test
    void aTenantThatIsNotAPlainIdOrDomainHasNoEndpoint() {
        ClientCredentials injected = new ClientCredentials("tenant/../evil", "client-1", SECRET, "api://api-1/.default");

        assertThrows(IllegalArgumentException.class, injected::tokenUri);
    }
}
