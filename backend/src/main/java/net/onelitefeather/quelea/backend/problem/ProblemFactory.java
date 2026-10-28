package net.onelitefeather.quelea.backend.problem;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import jakarta.inject.Singleton;
import net.onelitefeather.quelea.backend.event.TraceContext;

import java.net.URI;
import java.util.List;

@Singleton
public class ProblemFactory {

    public Problem create(PartyProblem problem, String detail, HttpRequest<?> request) {
        return create(problem, detail, request.getPath(), null);
    }

    public Problem create(PartyProblem problem, String detail, String instance, List<Problem.FieldError> errors) {
        return new Problem(
                URI.create(Problem.TYPE_BASE + problem.code()),
                problem.title(),
                problem.status().getCode(),
                detail,
                instance,
                problem.code(),
                TraceContext.traceId(),
                errors == null || errors.isEmpty() ? null : errors
        );
    }

    public HttpResponse<Problem> response(Problem problem) {
        return HttpResponse.status(HttpStatus.valueOf(problem.status()))
                .body(problem)
                .contentType(MediaType.of(Problem.MEDIA_TYPE));
    }
}
