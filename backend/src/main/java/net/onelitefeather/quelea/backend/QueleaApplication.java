package net.onelitefeather.quelea.backend;

import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/** The bearer scheme is what Swagger UI's "Authorize" sends: an Entra ID app-only access token. */
@OpenAPIDefinition(
        info = @Info(title = "Quelea", version = "v1"),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT",
        description = "Entra ID app-only access token with the app role Quelea.Client.")
public class QueleaApplication {

    public static void main(String[] args) {
        Micronaut.run(QueleaApplication.class, args);
    }
}
