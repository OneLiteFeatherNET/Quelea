package net.onelitefeather.quelea.backend.problem;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

@Produces(Problem.MEDIA_TYPE)
@Singleton
public class PartyExceptionHandler implements ExceptionHandler<PartyException, HttpResponse<Problem>> {

    private final ProblemFactory factory;

    public PartyExceptionHandler(ProblemFactory factory) {
        this.factory = factory;
    }

    @Override
    public HttpResponse<Problem> handle(HttpRequest request, PartyException exception) {
        MutableHttpResponse<Problem> response = factory.response(factory.create(exception.problem(), exception.getMessage(), request));
        exception.retryAfter().ifPresent(wait -> response.header(HttpHeaders.RETRY_AFTER, String.valueOf(wait.toSeconds())));
        return response;
    }
}
