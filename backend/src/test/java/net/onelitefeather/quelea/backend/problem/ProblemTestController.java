package net.onelitefeather.quelea.backend.problem;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.validation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import net.onelitefeather.quelea.backend.security.Roles;

/** Throws each kind of error the global handlers must map. Exists only in the test environment. */
@Requires(env = "test")
@Validated
@Controller("/test")
@Secured(Roles.CLIENT)
public class ProblemTestController {

    @Get("/party")
    public String party() {
        throw new PartyException(PartyProblem.NOT_LEADER, "Only the leader may kick.");
    }

    @Get("/internal")
    public String internal() {
        throw new IllegalStateException("database password is hunter2");
    }

    @Get("/validation")
    public String validation(@NotBlank @QueryValue String name) {
        return name;
    }

    @Get("/number")
    public String number(@QueryValue int n) {
        return String.valueOf(n);
    }

    @Post("/body")
    public String body(@Body @Valid TestBody body) {
        return body.message();
    }

    @Serdeable
    public record TestBody(@NotBlank String message) {
    }
}
