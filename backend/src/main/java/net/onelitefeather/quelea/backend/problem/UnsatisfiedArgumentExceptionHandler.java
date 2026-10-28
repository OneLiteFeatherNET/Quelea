package net.onelitefeather.quelea.backend.problem;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.UnsatisfiedArgumentHandler;
import jakarta.inject.Singleton;

@Produces(Problem.MEDIA_TYPE)
@Singleton
@Replaces(UnsatisfiedArgumentHandler.class)
public class UnsatisfiedArgumentExceptionHandler extends BadRequestExceptionHandler<UnsatisfiedArgumentException> {

    public UnsatisfiedArgumentExceptionHandler(ProblemFactory factory) {
        super(factory, "A required request parameter or body is missing or invalid.");
    }
}
