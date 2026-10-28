package net.onelitefeather.quelea.velocity.http;

import com.sun.net.httpserver.HttpServer;
import net.onelitefeather.quelea.velocity.auth.ClientCredentials;
import net.onelitefeather.quelea.velocity.auth.ClientCredentialsTokenProvider;
import net.onelitefeather.quelea.velocity.auth.MutableClock;
import net.onelitefeather.quelea.velocity.auth.TokenTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpQueleaGatewayTest {

    private static final String PARTY_JSON = """
            {"id":"p-1","leaderId":"leader-1",
             "members":[{"id":"leader-1","name":"Alice","joinedAt":"2026-10-28T10:00:00Z"},
                        {"id":"member-1","name":"Bob","joinedAt":"2026-10-28T10:01:00Z"}],
             "createdAt":"2026-10-28T10:00:00Z"}
            """;

    /** A reply that closes the connection without answering, as a timed-out or reset request looks to the client. */
    private static final Reply DROP = new Reply(0, "", "");

    private record Reply(int status, String contentType, String body) {
    }

    /** What the service received of one request. */
    private record Received(String method, String authorization, String idempotencyKey) {
    }

    private HttpServer server;
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedDeque<Reply> queued = new ConcurrentLinkedDeque<>();
    private volatile Reply reply = new Reply(201, "application/json", PARTY_JSON);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            received.add(new Received(exchange.getRequestMethod(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("Idempotency-Key")));
            Reply current = queued.isEmpty() ? reply : queued.poll();
            if (current == DROP) {
                exchange.close();
                return;
            }
            byte[] body = current.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", current.contentType());
            exchange.sendResponseHeaders(current.status(), body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private HttpQueleaGateway gateway() {
        return gateway(Optional.empty());
    }

    private HttpQueleaGateway gateway(Optional<ClientCredentialsTokenProvider> tokens) {
        return new HttpQueleaGateway("http://" + server.getAddress().getAddress().getHostAddress() + ":"
                + server.getAddress().getPort(), HttpClient.newHttpClient(), tokens);
    }

    /** A provider whose n-th token request is answered with the token {@code tok-n}. */
    private static Optional<ClientCredentialsTokenProvider> tokens() {
        AtomicInteger issued = new AtomicInteger();
        TokenTransport endpoint = (uri, form) -> new TokenTransport.Response(200,
                "{\"token_type\":\"Bearer\",\"expires_in\":3600,\"access_token\":\"tok-" + issued.incrementAndGet() + "\"}");
        return Optional.of(new ClientCredentialsTokenProvider(
                new ClientCredentials("tenant-1", "client-1", "secret-1", "api://api-1/.default"),
                endpoint, new MutableClock()));
    }

    @Test
    void parsesThePartyOfACreateCall() {
        Party party = gateway().createParty("leader-1");

        assertEquals("p-1", party.id());
        assertEquals("leader-1", party.leaderId());
        assertEquals(2, party.members().size());
        assertEquals("Bob", party.members().get(1).name());
    }

    @Test
    void sendsTheLeaderAsJson() {
        gateway().createParty("leader-1");

        assertTrue(lastRequestBody.get().contains("\"leaderId\":\"leader-1\""), lastRequestBody.get());
    }

    @Test
    void mapsAProblemDocumentToItsCode() {
        reply = new Reply(403, "application/problem+json", """
                {"type":"https://github.com/OneLiteFeatherNET/Quelea/blob/main/docs/problems.md#not-leader",
                 "title":"Not the leader","status":403,"detail":"only the leader may kick","code":"not-leader"}
                """);

        QueleaProblemException problem = assertThrows(QueleaProblemException.class,
                () -> gateway().kick("p-1", "member-1", "member-2"));

        assertEquals("not-leader", problem.code());
        assertEquals(403, problem.status());
        assertEquals("only the leader may kick", problem.detail());
    }

    @Test
    void notInPartyIsAnEmptyOptionalNotAnError() {
        reply = new Reply(404, "application/problem+json",
                "{\"status\":404,\"code\":\"not-in-party\",\"detail\":\"no party\"}");

        Optional<Party> party = gateway().partyOf("solo-player");

        assertFalse(party.isPresent());
    }

    @Test
    void parsesAnInviteList() {
        reply = new Reply(200, "application/json", """
                [{"partyId":"p-1","inviterId":"leader-1","inviterName":"Alice","targetId":"t-1",
                  "targetName":"Carol","expiresAt":"2026-10-28T10:01:00Z"}]
                """);

        List<Invite> invites = gateway().invites("t-1");

        assertEquals(1, invites.size());
        assertEquals("Alice", invites.get(0).inviterName());
    }

    @Test
    void aServerErrorWithoutProblemDocumentIsUnavailable() {
        reply = new Reply(500, "text/html", "<html>boom</html>");

        assertThrows(QueleaUnavailableException.class, () -> gateway().heartbeat("proxy-1"));
    }

    @Test
    void aMalformedBodyIsUnavailable() {
        reply = new Reply(200, "application/json", "not json at all");

        assertThrows(QueleaUnavailableException.class, () -> gateway().createParty("leader-1"));
    }

    @Test
    void connectionRefusedIsUnavailable() throws IOException {
        HttpServer closed = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        int port = closed.getAddress().getPort();
        closed.stop(0);
        HttpQueleaGateway gateway = new HttpQueleaGateway("http://127.0.0.1:" + port, HttpClient.newHttpClient(),
                Optional.empty());

        assertThrows(QueleaUnavailableException.class, () -> gateway.createParty("leader-1"));
    }

    @Test
    void everyRequestCarriesTheBearerToken() {
        HttpQueleaGateway gateway = gateway(tokens());

        gateway.createParty("leader-1");
        gateway.heartbeat("proxy-1");

        assertEquals("Bearer tok-1", received.get(0).authorization(), "a command");
        assertEquals("Bearer tok-1", received.get(1).authorization(), "a presence call");
    }

    @Test
    void noAuthorizationHeaderIsSentWithoutCredentials() {
        gateway().createParty("leader-1");

        assertNull(received.get(0).authorization(), "no credentials, no token");
    }

    @Test
    void aUnauthorizedAnswerRenewsTheTokenAndRepeatsTheRequestOnce() {
        queued.add(new Reply(401, "application/problem+json", "{\"status\":401,\"code\":\"unauthorized\"}"));

        Party party = gateway(tokens()).createParty("leader-1");

        assertEquals("p-1", party.id(), "the repeated request succeeds");
        assertEquals(2, received.size(), "one request, one repeat");
        assertEquals("Bearer tok-1", received.get(0).authorization(), "the rejected token was sent first");
        assertEquals("Bearer tok-2", received.get(1).authorization(), "the repeat carries the renewed token");
    }

    @Test
    void aSecondUnauthorizedAnswerIsReportedWithoutAFurtherRepeat() {
        queued.add(new Reply(401, "application/problem+json", "{\"status\":401,\"code\":\"unauthorized\"}"));
        queued.add(new Reply(401, "application/problem+json", "{\"status\":401,\"code\":\"unauthorized\"}"));

        QueleaProblemException problem = assertThrows(QueleaProblemException.class,
                () -> gateway(tokens()).createParty("leader-1"));

        assertEquals("unauthorized", problem.code());
        assertEquals(2, received.size(), "no loop: one renewal only");
    }

    @Test
    void everyPostCarriesAFreshIdempotencyKey() {
        HttpQueleaGateway gateway = gateway();

        gateway.createParty("leader-1");
        gateway.createParty("leader-1");

        String first = received.get(0).idempotencyKey();
        assertEquals("POST", received.get(0).method());
        assertNotNull(first, "a command carries a key");
        assertEquals(first, UUID.fromString(first).toString(), "the key is a UUID");
        assertNotEquals(first, received.get(1).idempotencyKey(), "each command gets its own key");
    }

    @Test
    void aPostAfterAnIoErrorIsRepeatedOnceWithTheSameKey() {
        queued.add(DROP);

        Party party = gateway().createParty("leader-1");

        assertEquals("p-1", party.id(), "the repeat answers the command");
        assertEquals(2, received.size(), "the first attempt and one repeat");
        assertNotNull(received.get(0).idempotencyKey());
        assertEquals(received.get(0).idempotencyKey(), received.get(1).idempotencyKey(),
                "the repeat reuses the key, so the service runs the command once");
    }

    @Test
    void aPostThatFailsTwiceIsUnavailableAfterExactlyTwoAttempts() {
        queued.add(DROP);
        queued.add(DROP);

        assertThrows(QueleaUnavailableException.class, () -> gateway().createParty("leader-1"));

        assertEquals(2, received.size(), "no third attempt");
    }

    @Test
    void aPresenceCallIsNotRepeatedAfterAnIoError() {
        queued.add(DROP);

        assertThrows(QueleaUnavailableException.class, () -> gateway().heartbeat("proxy-1"));

        assertEquals(1, received.size(), "a PUT is sent once");
        assertNull(received.get(0).idempotencyKey(), "presence calls carry no key");
    }

    @Test
    void aRequestStillInProgressIsUnavailableNotADomainError() {
        queued.add(new Reply(409, "application/problem+json",
                "{\"status\":409,\"code\":\"request-in-progress\",\"detail\":\"running\"}"));

        assertThrows(QueleaUnavailableException.class, () -> gateway().createParty("leader-1"));
    }
}
