package net.onelitefeather.quelea.backend.api;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import net.onelitefeather.quelea.backend.security.TestJwtIssuer;
import net.onelitefeather.quelea.backend.support.ApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The raw event stream on the wire. The stream is read line by line on a virtual thread; every wait is a bounded
 * poll, so a missing message fails after {@link #WAIT} instead of hanging. Keep-alive is 100 ms for this class.
 */
@MicronautTest(rebuildContext = true)
@Property(name = "quelea.events.keepalive", value = "100ms")
class EventStreamTest {

    static final String PROXY = "proxy-stream";
    static final Duration WAIT = Duration.ofSeconds(5);
    /** The keep-alive as it appears on the wire: a comment line and the blank line that ends the message. */
    static final String KEEP_ALIVE = ": keep-alive\n\n";

    @Inject
    EmbeddedServer server;

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    TestJwtIssuer jwt;

    private final java.net.http.HttpClient wire = java.net.http.HttpClient.newHttpClient();
    private ApiClient api;
    private WireStream stream;

    @BeforeEach
    void setUp() {
        api = new ApiClient(client, jwt);
        stream = new WireStream(server.getURI().resolve("/v1/events?proxyId=" + PROXY), jwt.clientToken());
    }

    @AfterEach
    void tearDown() {
        stream.close();
    }

    @Test
    void idleStreamReceivesKeepAliveAsSseCommentLine() {
        String received = stream.awaitText(KEEP_ALIVE);

        assertTrue(received.contains(KEEP_ALIVE),
                "an idle stream must send an SSE comment line followed by a blank line, received: " + received);
    }

    @Test
    void streamReceivesInvitedEventWithIdAndName() {
        UUID leader = api.login(PROXY, name("lead"));
        String targetName = name("tgt");
        api.login(PROXY, targetName);
        // The first keep-alive proves the server has subscribed this stream to the broadcaster.
        stream.awaitText(KEEP_ALIVE);

        api.postJson("/v1/parties/invites", "{\"actorId\":\"" + leader + "\",\"targetName\":\"" + targetName + "\"}");

        // The data line follows the event line, so wait for the invited player's name in the payload.
        String received = stream.awaitText("\"targetName\":\"" + targetName + "\"");
        assertTrue(received.contains("event: party.invited\n"), "the invite must be sent as party.invited, received: " + received);
        assertTrue(received.contains("id: "), "every event must carry its id, received: " + received);
    }

    @Test
    void streamOpensForClientIdWithoutProxyId() {
        WireStream lobby = new WireStream(server.getURI().resolve("/v1/events?clientId=lobby-wire"));
        try {
            String received = lobby.awaitText(KEEP_ALIVE);

            assertTrue(received.contains(KEEP_ALIVE), "a client that only sends clientId must get a stream, received: " + received);
        } finally {
            lobby.close();
        }
    }

    static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    /** One open stream: a virtual thread copies its lines into a queue; {@link #awaitText} polls the queue. */
    private final class WireStream {

        private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        private final StringBuilder received = new StringBuilder();
        private final AtomicReference<InputStream> body = new AtomicReference<>();

        private final String token;

        WireStream(URI uri, String token) {
            this.token = token;
            Thread.startVirtualThread(() -> read(uri));
        }

        private void read(URI uri) {
            HttpRequest request = HttpRequest.newBuilder(uri).header("Accept", "text/event-stream")
                    .header("Authorization", "Bearer " + token).GET().build();
            try {
                HttpResponse<InputStream> response = wire.send(request, BodyHandlers.ofInputStream());
                body.set(response.body());
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                }
            } catch (IOException | InterruptedException e) {
                // The stream is closed at the end of each test; the reader ends with it.
            }
        }

        /** Returns everything received so far once it contains {@code text}; fails after {@link #WAIT}. */
        String awaitText(String text) {
            long deadline = System.nanoTime() + WAIT.toNanos();
            try {
                while (received.indexOf(text) < 0) {
                    String line = lines.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
                    if (line == null) {
                        fail("no '" + text + "' on the stream within " + WAIT + ", received: " + received);
                    }
                    received.append(line).append('\n');
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted while waiting for '" + text + "'");
            }
            return received.toString();
        }

        void close() {
            InputStream open = body.get();
            if (open != null) {
                try {
                    open.close();
                } catch (IOException ignored) {
                    // Closing an already finished stream is harmless.
                }
            }
        }
    }
}
