package net.onelitefeather.quelea.backend.security;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.security.token.DefaultRolesFinder;
import io.micronaut.security.token.RolesFinder;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Maps the token's {@code roles} claim to the roles the routes check. Only app-only tokens are accepted: a
 * delegated token (one that carries {@code scp}, a signed-in user) gets no role at all, so it is refused with
 * 403 even when it also carries {@link Roles#CLIENT}.
 */
@Singleton
@Replaces(DefaultRolesFinder.class)
public class QueleaRolesFinder implements RolesFinder {

    static final String SCOPE_CLAIM = "scp";
    static final String ROLES_CLAIM = "roles";

    @Override
    public List<String> resolveRoles(Map<String, Object> claims) {
        if (claims == null || claims.containsKey(SCOPE_CLAIM)) {
            return List.of();
        }
        Object roles = claims.get(ROLES_CLAIM);
        if (roles instanceof Collection<?> values && values.contains(Roles.CLIENT)) {
            return List.of(Roles.CLIENT);
        }
        return List.of();
    }
}
