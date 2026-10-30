package net.onelitefeather.quelea.velocity.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The gateway's answers for the join routes: 201 is a sent request, 200 a direct join, 429 carries Retry-After. */
class JoinRequestGatewayTest {

    private static final String PARTY_JSON = """
            {"id":"p-1","leaderId":"leader-1",
             "members":[{"id":"leader-1","name":"Alice","joinedAt":"2026-10-28T10:00:00Z"}],
             "createdAt":"2026-10-28T10:00:00Z"}
            """;

    private HttpServer server;
    private volatile int status = 200;
    private volatile String body = PARTY_JSON;
    private volatile String retryAfter;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            if (retryAfter != null) {
                exchange.getResponseHeaders().add("Retry-After", retryAfter);
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
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
        return new HttpQueleaGateway("http://" + server.getAddress().getAddress().getHostAddress() + ":"
                + server.getAddress().getPort(), HttpClient.newHttpClient(), Optional.empty());
    }

    @Test
    void aCreated_answerIsASentRequestWithItsExpiry() {
        status = 201;
        body = """
                {"partyId":"p-1","requesterId":"req-1","requesterName":"Bob","expiresAt":"2026-10-28T10:01:00Z"}
                """;

        JoinResult result = gateway().request("req-1", "Alice");

        JoinResult.Sent sent = assertInstanceOf(JoinResult.Sent.class, result);
        assertEquals(Instant.parse("2026-10-28T10:01:00Z"), sent.expiresAt());
    }

    @Test
    void anOk_answerIsADirectJoinWithTheParty() {
        status = 200;

        JoinResult result = gateway().request("req-1", "Alice");

        JoinResult.Joined joined = assertInstanceOf(JoinResult.Joined.class, result);
        assertEquals("p-1", joined.party().id());
    }

    @Test
    void aCooldownAnswerCarriesTheRetryAfterSeconds() {
        status = 429;
        retryAfter = "42";
        body = """
                {"type":"https://github.com/OneLiteFeatherNET/Quelea/blob/main/docs/problems.md#join-request-cooldown",
                 "title":"Join request cooldown","status":429,"detail":"wait","code":"join-request-cooldown"}
                """;

        QueleaProblemException problem = org.junit.jupiter.api.Assertions.assertThrows(QueleaProblemException.class,
                () -> gateway().request("req-1", "Alice"));

        assertEquals("join-request-cooldown", problem.code());
        assertEquals(42L, problem.retryAfterSeconds().orElseThrow());
    }

    @Test
    void aProblemWithoutRetryAfterHasNoWait() {
        status = 409;
        body = """
                {"title":"Party is not public","status":409,"detail":"x","code":"party-not-public"}
                """;

        QueleaProblemException problem = org.junit.jupiter.api.Assertions.assertThrows(QueleaProblemException.class,
                () -> gateway().join("req-1", "Alice"));

        assertTrue(problem.retryAfterSeconds().isEmpty(), "no Retry-After header means no wait");
    }
}
