package net.onelitefeather.quelea.backend.problem;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.UnsatisfiedRouteHandler;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;
import jakarta.inject.Singleton;

@Produces(Problem.MEDIA_TYPE)
@Singleton
@Replaces(UnsatisfiedRouteHandler.class)
public class UnsatisfiedRouteExceptionHandler extends BadRequestExceptionHandler<UnsatisfiedRouteException> {

    public UnsatisfiedRouteExceptionHandler(ProblemFactory factory) {
        super(factory, "A required request parameter or body is missing or invalid.");
    }
}
