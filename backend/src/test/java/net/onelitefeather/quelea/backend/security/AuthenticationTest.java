package net.onelitefeather.quelea.backend.security;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import net.onelitefeather.quelea.backend.support.ApiClient;
import net.onelitefeather.quelea.backend.support.ApiClient.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bearer token rules of the API, end to end, with self-signed tokens and no network. */
@MicronautTest
class AuthenticationTest {

    private static final String PATH = "/v1/parties/";
    private static final String OTHER_TENANT = "00000000-0000-4000-8000-0000000000bb";

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    TestJwtIssuer jwt;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(client, jwt);
    }

    private Response getWith(String token) {
        return api.send(HttpRequest.GET(PATH + UUID.randomUUID()).bearerAuth(token));
    }

    @Test
    void aValidClientTokenPassesAuthentication() {
        Response response = getWith(jwt.clientToken());

        assertEquals("party-not-found", response.problemCode(), "a valid token reaches the route, which has no such party");
    }

    @Test
    void aRequestWithoutATokenAnswers401Unauthorized() {
        Response response = api.sendAnonymously(HttpRequest.GET(PATH + UUID.randomUUID()));

        assertEquals(401, response.status(), "no Authorization header");
        assertEquals("unauthorized", response.problemCode());
        assertEquals("Bearer", response.challenge(), "without a token the challenge names no error");
    }

    @Test
    void anExpiredTokenAnswers401WithInvalidTokenChallenge() {
        String token = jwt.appOnly().roles(Roles.CLIENT).expiredAt(Instant.parse("2000-01-01T00:00:00Z")).sign();

        Response response = getWith(token);

        assertEquals(401, response.status(), "a token that expired in 2000");
        assertEquals("Bearer error=\"invalid_token\"", response.challenge(), "a sent token is named as invalid");
    }

    @Test
    void aTokenFromAnotherIssuerAnswers401() {
        String token = jwt.appOnly().roles(Roles.CLIENT).claim("iss", "https://evil.example.org/v2.0").sign();

        assertEquals(401, getWith(token).status(), "wrong issuer");
    }

    @Test
    void aTokenForAnotherAudienceAnswers401() {
        String token = jwt.appOnly().roles(Roles.CLIENT).claim("aud", "some-other-api").sign();

        assertEquals(401, getWith(token).status(), "wrong audience");
    }

    @Test
    void aTokenOfAnotherTenantAnswers401() {
        String token = jwt.appOnly().roles(Roles.CLIENT).claim("tid", OTHER_TENANT).sign();

        assertEquals(401, getWith(token).status(), "wrong tenant");
    }

    @Test
    void aTokenSignedByAnUnknownKeyAnswers401() {
        String token = jwt.appOnly().roles(Roles.CLIENT).signedByAnotherKey().sign();

        assertEquals(401, getWith(token).status(), "signature from a key the application does not trust");
    }

    @Test
    void aTokenWithAlgNoneAnswers401() {
        String token = jwt.appOnly().roles(Roles.CLIENT).unsigned();

        assertEquals(401, getWith(token).status(), "alg none");
    }

    @Test
    void anHs256TokenSignedWithThePublicKeyAnswers401() {
        String token = jwt.appOnly().roles(Roles.CLIENT).hs256WithPublicKeyAsSecret();

        assertEquals(401, getWith(token).status(), "HMAC key confusion");
    }

    @Test
    void aValidTokenWithoutTheRoleAnswers403Forbidden() {
        String token = jwt.appOnly().without("roles").sign();

        Response response = getWith(token);

        assertEquals(403, response.status(), "a valid token without Quelea.Client");
        assertEquals("forbidden", response.problemCode());
    }

    @Test
    void aDelegatedTokenAnswers403EvenWithTheRole() {
        String token = jwt.appOnly().roles(Roles.CLIENT).claim("scp", "access_as_user").sign();

        Response response = getWith(token);

        assertEquals(403, response.status(), "a token with scp is a signed-in user, never a client");
        assertEquals("forbidden", response.problemCode());
    }

    @Test
    void healthStaysOpenWithoutAToken() {
        Response response = api.sendAnonymously(HttpRequest.GET("/health"));

        assertEquals(200, response.status(), "orchestrator probes carry no token");
    }

    @Test
    void swaggerUiStaysOpenWithoutAToken() {
        Response response = api.sendAnonymously(HttpRequest.GET("/swagger-ui/index.html"));

        assertEquals(200, response.status(), "the API documentation is public");
        assertTrue(response.body().contains("swagger"), "the Swagger UI page is served");
    }
}
