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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpQueleaGatewayTest {

    private static final String PARTY_JSON = """
            {"id":"p-1","leaderId":"leader-1",
             "members":[{"id":"leader-1","name":"Alice","joinedAt":"2026-10-28T10:00:00Z"},
                        {"id":"member-1","name":"Bob","joinedAt":"2026-10-28T10:01:00Z"}],
             "createdAt":"2026-10-28T10:00:00Z"}
            """;

    private HttpServer server;
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private volatile Reply reply = new Reply(201, "application/json", PARTY_JSON);

    private record Reply(int status, String contentType, String body) {
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", reply.contentType());
            exchange.sendResponseHeaders(reply.status(), body.length == 0 ? -1 : body.length);
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
        return new HttpQueleaGateway("http://" + server.getAddress().getAddress().getHostAddress() + ":"
                + server.getAddress().getPort(), HttpClient.newHttpClient());
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
        HttpQueleaGateway gateway = new HttpQueleaGateway("http://127.0.0.1:" + port, HttpClient.newHttpClient());

        assertThrows(QueleaUnavailableException.class, () -> gateway.createParty("leader-1"));
    }
}
