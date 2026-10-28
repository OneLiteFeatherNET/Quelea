package net.onelitefeather.quelea.backend.problem;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Last resort: logs the exception and answers with a fixed detail, never with the exception message. */
@Produces(Problem.MEDIA_TYPE)
@Singleton
public class InternalErrorExceptionHandler implements ExceptionHandler<Throwable, HttpResponse<Problem>> {

    static final String DETAIL = "An unexpected error occurred.";

    private static final Logger LOG = LoggerFactory.getLogger(InternalErrorExceptionHandler.class);

    private final ProblemFactory factory;

    public InternalErrorExceptionHandler(ProblemFactory factory) {
        this.factory = factory;
    }

    @Override
    public HttpResponse<Problem> handle(HttpRequest request, Throwable exception) {
        LOG.error("Unhandled exception for {} {}", request.getMethodName(), request.getPath(), exception);
        return factory.response(factory.create(PartyProblem.INTERNAL_ERROR, DETAIL, request));
    }
}
