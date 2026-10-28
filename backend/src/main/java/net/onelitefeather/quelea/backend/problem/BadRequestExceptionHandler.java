package net.onelitefeather.quelea.backend.problem;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.ExceptionHandler;

/**
 * Maps request-shape failures (unparsable body, unconvertible or missing parameter) to 400
 * {@code validation-failed}. Each concrete subclass replaces one of Micronaut's default handlers.
 */
abstract class BadRequestExceptionHandler<E extends Throwable> implements ExceptionHandler<E, HttpResponse<Problem>> {

    private final ProblemFactory factory;
    private final String detail;

    protected BadRequestExceptionHandler(ProblemFactory factory, String detail) {
        this.factory = factory;
        this.detail = detail;
    }

    @Override
    public HttpResponse<Problem> handle(HttpRequest request, E exception) {
        return factory.response(factory.create(PartyProblem.VALIDATION_FAILED, detail, request.getPath(), null));
    }
}
