package net.onelitefeather.quelea.backend.problem;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.validation.exceptions.ConstraintExceptionHandler;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import java.util.List;

@Produces(Problem.MEDIA_TYPE)
@Singleton
@Replaces(ConstraintExceptionHandler.class)
public class ValidationExceptionHandler implements ExceptionHandler<ConstraintViolationException, HttpResponse<Problem>> {

    private static final String DETAIL = "The request failed validation.";

    private final ProblemFactory factory;

    public ValidationExceptionHandler(ProblemFactory factory) {
        this.factory = factory;
    }

    @Override
    public HttpResponse<Problem> handle(HttpRequest request, ConstraintViolationException exception) {
        List<Problem.FieldError> errors = exception.getConstraintViolations().stream()
                .map(ValidationExceptionHandler::toFieldError)
                .toList();
        return factory.response(factory.create(PartyProblem.VALIDATION_FAILED, DETAIL, request.getPath(), errors));
    }

    /** Violations of method validation are named {@code method.arg0.field}; the field is the last path node. */
    static Problem.FieldError toFieldError(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        String field = path.substring(path.lastIndexOf('.') + 1);
        return new Problem.FieldError(field, violation.getMessage());
    }
}
