package net.onelitefeather.quelea.backend.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

import java.util.UUID;

/** Sends requests through the embedded server and returns status, content type and body as plain values. */
public final class ApiClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient client;

    public ApiClient(HttpClient client) {
        this.client = client;
    }

    public Response send(MutableHttpRequest<?> request) {
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

    public record Response(int status, String contentType, String body) {

        static Response of(HttpResponse<?> response) {
            return new Response(
                    response.getStatus().getCode(),
                    response.getContentType().map(MediaType::toString).orElse(""),
                    response.getBody(String.class).orElse(""));
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
