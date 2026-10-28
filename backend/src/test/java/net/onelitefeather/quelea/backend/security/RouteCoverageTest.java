package net.onelitefeather.quelea.backend.security;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.security.annotation.Secured;
import io.micronaut.web.router.Router;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every route of the application has a security rule, except the anonymous health probes. */
@MicronautTest
class RouteCoverageTest {

    @Inject
    Router router;

    @Test
    void everyRouteRequiresTheClientRoleExceptHealth() {
        Set<String> withoutRole = new TreeSet<>();
        Set<String> secured = new TreeSet<>();
        router.uriRoutes().filter(route -> !"HEAD".equals(route.getHttpMethodName())).forEach(route -> {
            String key = route.getHttpMethodName() + " " + route.getUriMatchTemplate();
            Set<String> roles = Set.of(route.getAnnotationMetadata().stringValues(Secured.class));
            if (roles.equals(Set.of(Roles.CLIENT))) {
                secured.add(key);
            } else {
                withoutRole.add(key);
            }
        });

        Set<String> unexpected = withoutRole.stream()
                .filter(route -> !route.endsWith(" /health") && !route.contains(" /health/"))
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(Set.of(), unexpected, "routes without the Quelea.Client rule would be open");
        assertTrue(secured.size() >= 16, "the whole API is registered as secured routes: " + secured);
    }
}
