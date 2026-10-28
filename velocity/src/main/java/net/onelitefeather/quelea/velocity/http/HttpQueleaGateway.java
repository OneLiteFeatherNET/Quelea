package net.onelitefeather.quelea.velocity.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * {@link QueleaGateway} over plain HTTP and JSON. The client is injected so tests can point it at a local server.
 */
public final class HttpQueleaGateway implements QueleaGateway {

    /** Upper bound for every request except the event stream, which is open-ended. */
    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private static final String NOT_IN_PARTY = "not-in-party";

    private final String baseUrl;
    private final HttpClient client;

    public HttpQueleaGateway(String baseUrl, HttpClient client) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.client = client;
    }

    /**
     * @return a client suitable for the gateway and the event stream: HTTP/1.1, connect timeout 3 seconds
     */
    public static HttpClient newClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    /**
     * @return the URI of {@code GET /v1/events} for one proxy
     */
    public static URI eventsUri(String baseUrl, String proxyId) {
        return URI.create(stripTrailingSlash(baseUrl) + "/v1/events?proxyId=" + encode(proxyId));
    }

    @Override
    public Party createParty(String leaderId) {
        JsonObject body = new JsonObject();
        body.addProperty("leaderId", leaderId);
        return decode(call("POST", "/v1/parties", body), QueleaJson::party);
    }

    @Override
    public Optional<Party> partyOf(String playerId) {
        try {
            return Optional.of(decode(call("GET", "/v1/players/" + encode(playerId) + "/party", null), QueleaJson::party));
        } catch (QueleaProblemException e) {
            if (NOT_IN_PARTY.equals(e.code())) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public Invite invite(String actorId, String targetName) {
        JsonObject body = new JsonObject();
        body.addProperty("actorId", actorId);
        body.addProperty("targetName", targetName);
        return decode(call("POST", "/v1/parties/invites", body), QueleaJson::invite);
    }

    @Override
    public List<Invite> invites(String playerId) {
        return decodeArray(call("GET", "/v1/players/" + encode(playerId) + "/invites", null), array -> {
            List<Invite> invites = new ArrayList<>(array.size());
            for (JsonElement element : array) {
                invites.add(QueleaJson.invite(element.getAsJsonObject()));
            }
            return invites;
        });
    }

    @Override
    public Party acceptInvite(String playerId, String partyId) {
        return decode(call("POST", inviteRoute(playerId, partyId) + "/accept", null), QueleaJson::party);
    }

    @Override
    public void denyInvite(String playerId, String partyId) {
        call("POST", inviteRoute(playerId, partyId) + "/deny", null);
    }

    @Override
    public void leave(String playerId) {
        call("POST", "/v1/players/" + encode(playerId) + "/party/leave", null);
    }

    @Override
    public Party kick(String partyId, String actorId, String targetId) {
        return decode(call("POST", partyRoute(partyId) + "/kick", actorTarget(actorId, targetId)), QueleaJson::party);
    }

    @Override
    public Party promote(String partyId, String actorId, String targetId) {
        return decode(call("POST", partyRoute(partyId) + "/promote", actorTarget(actorId, targetId)), QueleaJson::party);
    }

    @Override
    public void disband(String partyId, String actorId) {
        JsonObject body = new JsonObject();
        body.addProperty("actorId", actorId);
        call("POST", partyRoute(partyId) + "/disband", body);
    }

    @Override
    public void warp(String partyId, String actorId, String server) {
        JsonObject body = new JsonObject();
        body.addProperty("actorId", actorId);
        body.addProperty("server", server);
        call("POST", partyRoute(partyId) + "/warp", body);
    }

    @Override
    public void chat(String partyId, String actorId, String message) {
        JsonObject body = new JsonObject();
        body.addProperty("actorId", actorId);
        body.addProperty("message", message);
        call("POST", partyRoute(partyId) + "/chat", body);
    }

    @Override
    public void snapshot(String proxyId, List<PlayerRef> players) {
        JsonArray array = new JsonArray();
        for (PlayerRef player : players) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", player.id());
            entry.addProperty("name", player.name());
            array.add(entry);
        }
        call("PUT", "/v1/proxies/" + encode(proxyId) + "/players", array);
    }

    @Override
    public void login(String proxyId, String playerId, String name) {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        call("PUT", playerRoute(proxyId, playerId), body);
    }

    @Override
    public void disconnect(String proxyId, String playerId) {
        call("DELETE", playerRoute(proxyId, playerId), null);
    }

    @Override
    public void heartbeat(String proxyId) {
        call("PUT", "/v1/proxies/" + encode(proxyId) + "/heartbeat", null);
    }

    private HttpResponse<String> call(String method, String path, JsonElement body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json, application/problem+json");
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        }
        HttpResponse<String> response;
        try {
            response = client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new QueleaUnavailableException("party service unreachable: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QueleaUnavailableException("interrupted while calling the party service", e);
        }
        if (response.statusCode() / 100 == 2) {
            return response;
        }
        throw failure(response);
    }

    private static RuntimeException failure(HttpResponse<String> response) {
        int status = response.statusCode();
        try {
            JsonObject problem = QueleaJson.object(response.body());
            String code = QueleaJson.optionalString(problem, "code");
            if (code != null) {
                return new QueleaProblemException(code, status, QueleaJson.optionalString(problem, "detail"));
            }
        } catch (RuntimeException ignored) {
            // Not a problem document: handled below as unavailable.
        }
        return new QueleaUnavailableException("party service answered HTTP " + status + " without a problem document");
    }

    private static <T> T decode(HttpResponse<String> response, Function<JsonObject, T> mapper) {
        try {
            return mapper.apply(QueleaJson.object(response.body()));
        } catch (RuntimeException e) {
            throw new QueleaUnavailableException("unexpected response from the party service", e);
        }
    }

    private static <T> T decodeArray(HttpResponse<String> response, Function<JsonArray, T> mapper) {
        try {
            return mapper.apply(QueleaJson.array(response.body()));
        } catch (RuntimeException e) {
            throw new QueleaUnavailableException("unexpected response from the party service", e);
        }
    }

    private static JsonObject actorTarget(String actorId, String targetId) {
        JsonObject body = new JsonObject();
        body.addProperty("actorId", actorId);
        body.addProperty("targetId", targetId);
        return body;
    }

    private static String partyRoute(String partyId) {
        return "/v1/parties/" + encode(partyId);
    }

    private static String inviteRoute(String playerId, String partyId) {
        return "/v1/players/" + encode(playerId) + "/invites/" + encode(partyId);
    }

    private static String playerRoute(String proxyId, String playerId) {
        return "/v1/proxies/" + encode(proxyId) + "/players/" + encode(playerId);
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
