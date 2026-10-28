package net.onelitefeather.quelea.backend.problem;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.security.authentication.AuthorizationException;
import io.micronaut.security.authentication.DefaultAuthorizationExceptionHandler;
import jakarta.inject.Singleton;

/**
 * Answers the authentication failures of Micronaut Security as Problem Details (RFC 9457): 401 {@code unauthorized}
 * with the RFC 6750 challenge, or 403 {@code forbidden} for an authenticated caller without the role. A sent token
 * is named only as {@code error="invalid_token"} in the challenge; the token itself is never echoed.
 */
@Produces(Problem.MEDIA_TYPE)
@Singleton
@Replaces(DefaultAuthorizationExceptionHandler.class)
public class AuthorizationExceptionHandler implements ExceptionHandler<AuthorizationException, HttpResponse<Problem>> {

    static final String CHALLENGE = "Bearer";
    static final String INVALID_TOKEN_CHALLENGE = "Bearer error=\"invalid_token\"";

    private final ProblemFactory factory;

    public AuthorizationExceptionHandler(ProblemFactory factory) {
        this.factory = factory;
    }

    @Override
    public HttpResponse<Problem> handle(HttpRequest request, AuthorizationException exception) {
        if (exception.isForbidden()) {
            return factory.response(factory.create(PartyProblem.FORBIDDEN,
                    "The token lacks the role this request needs.", request));
        }
        MutableHttpResponse<Problem> response = factory.response(factory.create(PartyProblem.UNAUTHORIZED,
                "A valid Bearer access token of the Quelea API is required.", request));
        return response.header(HttpHeaders.WWW_AUTHENTICATE, challenge(request));
    }

    static String challenge(HttpRequest<?> request) {
        String authorization = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
        boolean tokenSent = authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7);
        return tokenSent ? INVALID_TOKEN_CHALLENGE : CHALLENGE;
    }
}
