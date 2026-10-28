package net.onelitefeather.quelea.backend.security;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueleaRolesFinderTest {

    private final QueleaRolesFinder finder = new QueleaRolesFinder();

    @Test
    void theClientRoleOfAnAppOnlyTokenIsGranted() {
        assertEquals(List.of(Roles.CLIENT), finder.resolveRoles(Map.of("roles", List.of(Roles.CLIENT))),
                "an app-only token with the app role");
    }

    @Test
    void aDelegatedTokenIsGrantedNoRoleEvenWithTheClientRole() {
        assertEquals(List.of(), finder.resolveRoles(Map.of("scp", "access_as_user", "roles", List.of(Roles.CLIENT))),
                "a token with scp is refused whatever it carries");
    }

    @Test
    void anotherAppRoleGrantsNothing() {
        assertEquals(List.of(), finder.resolveRoles(Map.of("roles", List.of("Other.Role"))), "only Quelea.Client is known");
    }

    @Test
    void aTokenWithoutRolesGrantsNothing() {
        assertEquals(List.of(), finder.resolveRoles(Map.of()), "no roles claim");
    }

    @Test
    void missingClaimsGrantNothing() {
        assertEquals(List.of(), finder.resolveRoles(null), "no claims at all");
    }
}
