package net.onelitefeather.quelea.velocity.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Gets access tokens with the OAuth 2.0 client credentials grant and keeps the current one until shortly before it
 * expires. Callers that arrive while a token is being requested wait for that request instead of sending their own.
 */
public final class ClientCredentialsTokenProvider {

    /** A token is replaced this long before it expires. */
    public static final Duration SKEW = Duration.ofSeconds(60);

    private static final int DETAIL_LIMIT = 300;

    private final URI tokenUri;
    private final String form;
    private final TokenTransport transport;
    private final Clock clock;

    private String token;
    private Instant refreshAt = Instant.MIN;

    /**
     * @param credentials complete credentials
     * @param transport   sends the token request
     * @param clock       the time source
     * @throws IllegalArgumentException if the credentials are incomplete or name no valid endpoint
     */
    public ClientCredentialsTokenProvider(ClientCredentials credentials, TokenTransport transport, Clock clock) {
        if (!credentials.isComplete()) {
            throw new IllegalArgumentException("Incomplete credentials, missing: " + credentials.missing());
        }
        this.tokenUri = credentials.tokenUri();
        this.form = "grant_type=client_credentials"
                + "&client_id=" + encode(credentials.clientId())
                + "&client_secret=" + encode(credentials.clientSecret())
                + "&scope=" + encode(credentials.scope());
        this.transport = transport;
        this.clock = clock;
    }

    /**
     * @return a token that is valid now
     * @throws TokenException if none can be obtained
     */
    public synchronized String token() throws TokenException {
        if (this.token != null && this.clock.instant().isBefore(this.refreshAt)) {
            return this.token;
        }
        return refresh();
    }

    /**
     * Tells the provider the service refused this token, so the next {@link #token()} must not return it.
     */
    public synchronized void rejected(String rejected) {
        // A caller that is slow to report must not throw away the newer token another caller fetched.
        if (rejected.equals(this.token)) {
            this.token = null;
            this.refreshAt = Instant.MIN;
        }
    }

    private String refresh() throws TokenException {
        TokenTransport.Response response;
        try {
            response = this.transport.post(this.tokenUri, this.form);
        } catch (IOException e) {
            throw new TokenException("Token request to " + this.tokenUri + " failed: " + e.getMessage(), e);
        }
        if (response.status() / 100 != 2) {
            throw new TokenException("Token request to " + this.tokenUri + " was refused with HTTP "
                    + response.status() + errorDetail(response.body()));
        }
        JsonObject body;
        try {
            body = JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new TokenException("Token response from " + this.tokenUri + " is not a JSON object", e);
        }
        String accessToken = stringOf(body, "access_token");
        long expiresIn = longOf(body, "expires_in");
        if (accessToken.isEmpty() || expiresIn <= 0) {
            throw new TokenException("Token response from " + this.tokenUri + " has no access_token or expires_in");
        }
        this.token = accessToken;
        this.refreshAt = this.clock.instant().plusSeconds(expiresIn).minus(SKEW);
        return accessToken;
    }

    private static String errorDetail(String body) {
        try {
            JsonObject node = JsonParser.parseString(body).getAsJsonObject();
            String detail = (stringOf(node, "error") + " " + stringOf(node, "error_description")).strip();
            if (detail.isEmpty()) {
                return "";
            }
            return ": " + (detail.length() > DETAIL_LIMIT ? detail.substring(0, DETAIL_LIMIT) + "..." : detail);
        } catch (JsonParseException | IllegalStateException e) {
            return "";
        }
    }

    private static String stringOf(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static long longOf(JsonObject object, String name) {
        JsonElement value = object.get(name);
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsLong() : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
