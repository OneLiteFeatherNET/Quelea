package net.onelitefeather.quelea.backend.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupRequirementsTest {

    private static final String GUID = "774f6ffd-1111-4222-8333-444455556666";
    private static final String TENANT = "11111111-2222-4333-8444-555566667777";
    private static final String ISSUER = "https://login.microsoftonline.com/" + TENANT + "/v2.0";

    private static void checkProduction(boolean security, String issuer, String audience, String tenant) {
        StartupRequirementsGuard.check(security, issuer, audience, tenant, Set.of("prod"));
    }

    @Test
    void aSecureProductionConfigurationStarts() {
        assertDoesNotThrow(() -> checkProduction(true, ISSUER, GUID, TENANT), "https issuer, GUID audience, tenant");
    }

    @Test
    void developmentWithTheLocalDefaultsStarts() {
        assertDoesNotThrow(() -> StartupRequirementsGuard.check(false, "http://localhost:18090/quelea", "quelea-api", "",
                Set.of("dev")), "dev may run without security and with the placeholders");
    }

    @Test
    void testWithTheLocalDefaultsStarts() {
        assertDoesNotThrow(() -> StartupRequirementsGuard.check(true, "http://localhost:18090/quelea", "quelea-api", "",
                Set.of("test")), "test uses the placeholders");
    }

    @Test
    void disabledSecurityOutsideDevelopmentIsRefused() {
        Throwable failure = assertThrows(IllegalStateException.class, () -> checkProduction(false, ISSUER, GUID, TENANT));

        assertTrue(failure.getMessage().contains("micronaut.security.enabled"), "the message names the setting: " + failure.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://login.microsoftonline.com/tenant/v2.0", "http://localhost:18090/quelea", "https://localhost/quelea", ""})
    void anInsecureIssuerOutsideDevelopmentIsRefused(String issuer) {
        Throwable failure = assertThrows(IllegalStateException.class, () -> checkProduction(true, issuer, GUID, TENANT));

        assertTrue(failure.getMessage().contains("QUELEA_AUTH_ISSUER"), "the message names the setting: " + failure.getMessage());
    }

    @Test
    void anAudienceThatIsNotAGuidOutsideDevelopmentIsRefused() {
        Throwable failure = assertThrows(IllegalStateException.class, () -> checkProduction(true, ISSUER, "api://quelea", TENANT));

        assertTrue(failure.getMessage().contains("QUELEA_AUTH_AUDIENCE"), "the message names the setting: " + failure.getMessage());
    }

    @Test
    void aMissingTenantOutsideDevelopmentIsRefused() {
        Throwable failure = assertThrows(IllegalStateException.class, () -> checkProduction(true, ISSUER, GUID, " "));

        assertTrue(failure.getMessage().contains("QUELEA_AUTH_TENANT_ID"), "the message names the setting: " + failure.getMessage());
    }
}
