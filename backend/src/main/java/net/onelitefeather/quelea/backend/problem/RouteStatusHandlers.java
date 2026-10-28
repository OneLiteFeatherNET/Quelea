package net.onelitefeather.quelea.backend.problem;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;

/**
 * Status handlers for requests that match no route (404) or match a path with another method (405).
 * They are declared on a controller without routes, because Micronaut only looks up {@code @Error} methods
 * on controllers.
 */
@Controller
public class RouteStatusHandlers {

    private final ProblemFactory factory;

    public RouteStatusHandlers(ProblemFactory factory) {
        this.factory = factory;
    }

    @Error(status = HttpStatus.NOT_FOUND, global = true)
    public HttpResponse<Problem> notFound(HttpRequest<?> request) {
        return factory.response(factory.create(PartyProblem.NOT_FOUND, "No resource exists at " + request.getPath() + ".", request));
    }

    @Error(status = HttpStatus.METHOD_NOT_ALLOWED, global = true)
    public HttpResponse<Problem> methodNotAllowed(HttpRequest<?> request) {
        return factory.response(factory.create(PartyProblem.METHOD_NOT_ALLOWED,
                request.getMethodName() + " is not supported for " + request.getPath() + ".", request));
    }

    @Error(status = HttpStatus.UNSUPPORTED_MEDIA_TYPE, global = true)
    public HttpResponse<Problem> unsupportedMediaType(HttpRequest<?> request) {
        String contentType = request.getContentType().map(Object::toString).orElse("no content type");
        return factory.response(factory.create(PartyProblem.UNSUPPORTED_MEDIA_TYPE,
                "The body of " + request.getPath() + " must be application/json, not " + contentType + ".", request));
    }
}
