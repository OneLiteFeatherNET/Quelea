package net.onelitefeather.quelea.backend.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import net.onelitefeather.quelea.backend.security.TestJwtIssuer;

import java.util.UUID;

/** Sends requests through the embedded server and returns status, content type and body as plain values. */
public final class ApiClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient client;
    private final String token;

    public ApiClient(HttpClient client, TestJwtIssuer issuer) {
        this.client = client;
        this.token = issuer.clientToken();
    }

    /** Sends the request with a valid {@code Quelea.Client} token, unless the request already names an Authorization. */
    public Response send(MutableHttpRequest<?> request) {
        if (request.getHeaders().contains(HttpHeaders.AUTHORIZATION)) {
            return exchange(request);
        }
        return exchange(request.bearerAuth(this.token));
    }

    /** Sends the request without any Authorization header. */
    public Response sendAnonymously(MutableHttpRequest<?> request) {
        return exchange(request);
    }

    private Response exchange(MutableHttpRequest<?> request) {
        try {
            HttpResponse<String> response = client.toBlocking().exchange(request, String.class);
            return Response.of(response);
        } catch (HttpClientResponseException e) {
            return Response.of(e.getResponse());
        }
    }

    public Response postJson(String path, String json) {
        return send(HttpRequest.POST(path, json).contentType(MediaType.APPLICATION_JSON_TYPE));
    }

    public Response putJson(String path, String json) {
        return send(HttpRequest.PUT(path, json).contentType(MediaType.APPLICATION_JSON_TYPE));
    }

    /** Puts a player online on {@code proxyId} through the HTTP API and returns the player's id. */
    public UUID login(String proxyId, String name) {
        UUID id = UUID.randomUUID();
        Response response = putJson("/v1/proxies/" + proxyId + "/players/" + id, "{\"name\":\"" + name + "\"}");
        if (response.status() != 204) {
            throw new IllegalStateException("login failed: " + response);
        }
        return id;
    }

    /**
     * The status, content type, body, {@code WWW-Authenticate} challenge and {@code ETag} (each empty when absent) of a
     * reply.
     */
    public record Response(int status, String contentType, String body, String challenge, String etag, String retryAfter) {

        static Response of(HttpResponse<?> response) {
            return new Response(
                    response.getStatus().getCode(),
                    response.getContentType().map(MediaType::toString).orElse(""),
                    response.getBody(String.class).orElse(""),
                    java.util.Optional.ofNullable(response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).orElse(""),
                    java.util.Optional.ofNullable(response.getHeaders().get(HttpHeaders.ETAG)).orElse(""),
                    java.util.Optional.ofNullable(response.getHeaders().get(HttpHeaders.RETRY_AFTER)).orElse(""));
        }

        public JsonNode json() {
            return JSON.readTree(body);
        }

        /** The stable problem code of a problem+json body. */
        public String problemCode() {
            return json().path("code").asText();
        }
    }
}
