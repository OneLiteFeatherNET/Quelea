package net.onelitefeather.quelea.backend.problem;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class ProblemHandlerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    ObjectMapper objectMapper;

    @Test
    void domainErrorAnswersWithTheStatusOfItsProblem() {
        Result result = send(HttpRequest.GET("/test/party"));

        assertEquals(403, result.status(), "a NOT_LEADER problem must answer 403");
    }

    @Test
    void domainErrorAnswersWithProblemJson() {
        Result result = send(HttpRequest.GET("/test/party"));

        assertEquals(MediaType.of(Problem.MEDIA_TYPE).toString(), result.contentType(), "the content type must be application/problem+json");
    }

    @Test
    void domainErrorBodyCarriesCodeAndTypeUriEndingInCode() throws Exception {
        Problem problem = problemOf(send(HttpRequest.GET("/test/party")));

        assertEquals("not-leader", problem.code(), "the stable code must be the enum's slug");
        assertTrue(problem.type().toString().endsWith("#not-leader"), "the type URI must point at the code's docs section");
        assertEquals("Only the leader may kick.", problem.detail(), "the detail must be the exception message");
        assertEquals("/test/party", problem.instance(), "the instance must be the request path");
    }

    @Test
    void invalidQueryParameterAnswers400ValidationFailed() throws Exception {
        Result result = send(HttpRequest.GET("/test/validation?name="));
        Problem problem = problemOf(result);

        assertEquals(400, result.status(), "a constraint violation must answer 400");
        assertEquals("validation-failed", problem.code());
        assertTrue(problem.errors().stream().anyMatch(e -> e.field().equals("name")),
                "the errors must name the offending field without the method and argument prefix");
    }

    @Test
    void invalidBodyFieldAnswers400WithFieldError() throws Exception {
        Result result = send(HttpRequest.POST("/test/body", "{\"message\":\"\"}").contentType(MediaType.APPLICATION_JSON_TYPE));
        Problem problem = problemOf(result);

        assertEquals(400, result.status());
        assertEquals("validation-failed", problem.code());
        assertTrue(problem.errors().stream().anyMatch(e -> e.field().equals("message")),
                "a blank body field must be reported under its own name");
    }

    @Test
    void unconvertibleQueryParameterAnswers400ValidationFailed() throws Exception {
        Result result = send(HttpRequest.GET("/test/number?n=abc"));

        assertEquals(400, result.status(), "a value that does not convert must answer 400");
        assertEquals("validation-failed", problemOf(result).code());
    }

    @Test
    void missingRequiredParameterAnswers400ValidationFailed() throws Exception {
        Result result = send(HttpRequest.GET("/test/number"));

        assertEquals(400, result.status(), "a missing required parameter must answer 400");
        assertEquals("validation-failed", problemOf(result).code());
    }

    @Test
    void malformedJsonBodyAnswers400ValidationFailed() throws Exception {
        Result result = send(HttpRequest.POST("/test/body", "{not json").contentType(MediaType.APPLICATION_JSON_TYPE));

        assertEquals(400, result.status(), "a body that is not JSON must answer 400");
        assertEquals("validation-failed", problemOf(result).code());
    }

    @Test
    void unknownRouteAnswers404NotFoundAsProblem() throws Exception {
        Result result = send(HttpRequest.GET("/does/not/exist"));
        Problem problem = problemOf(result);

        assertEquals(404, result.status());
        assertEquals("not-found", problem.code());
        assertEquals(MediaType.of(Problem.MEDIA_TYPE).toString(), result.contentType());
    }

    @Test
    void wrongMethodAnswers405MethodNotAllowedAsProblem() throws Exception {
        Result result = send(HttpRequest.POST("/test/party", ""));
        Problem problem = problemOf(result);

        assertEquals(405, result.status(), "a known path with another method must answer 405");
        assertEquals("method-not-allowed", problem.code());
    }

    @Test
    void wrongContentTypeAnswers415UnsupportedMediaTypeAsProblem() throws Exception {
        Result result = send(HttpRequest.POST("/test/body", "message=hi")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE));
        Problem problem = problemOf(result);

        assertEquals(415, result.status(), "a body in a media type the route does not accept must answer 415");
        assertEquals("unsupported-media-type", problem.code());
        assertEquals(MediaType.of(Problem.MEDIA_TYPE).toString(), result.contentType());
    }

    @Test
    void unexpectedExceptionAnswers500WithFixedDetail() throws Exception {
        Result result = send(HttpRequest.GET("/test/internal"));
        Problem problem = problemOf(result);

        assertEquals(500, result.status());
        assertEquals("internal-error", problem.code());
        assertEquals("An unexpected error occurred.", problem.detail());
    }

    @Test
    void unexpectedExceptionMessageIsNotInTheBody() {
        Result result = send(HttpRequest.GET("/test/internal"));

        assertFalse(result.body().contains("hunter2"), "the exception message must never reach the response body");
    }

    private Result send(io.micronaut.http.MutableHttpRequest<?> request) {
        try {
            HttpResponse<String> response = client.toBlocking().exchange(request, String.class);
            return Result.of(response);
        } catch (HttpClientResponseException e) {
            return Result.of(e.getResponse());
        }
    }

    private Problem problemOf(Result result) throws Exception {
        assertNotNull(result.body(), "the response must have a body");
        return objectMapper.readValue(result.body(), Problem.class);
    }

    private record Result(int status, String contentType, String body) {
        static Result of(HttpResponse<?> response) {
            return new Result(
                    response.getStatus().getCode(),
                    response.getContentType().map(MediaType::toString).orElse(""),
                    response.getBody(String.class).orElse("")
            );
        }
    }
}
