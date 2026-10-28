package net.onelitefeather.quelea.backend.problem;

import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ConversionErrorHandler;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;

@Produces(Problem.MEDIA_TYPE)
@Singleton
@Replaces(ConversionErrorHandler.class)
public class ConversionErrorExceptionHandler extends BadRequestExceptionHandler<ConversionErrorException> {

    public ConversionErrorExceptionHandler(ProblemFactory factory) {
        super(factory, "A request parameter has an invalid value.");
    }
}
