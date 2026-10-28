package net.onelitefeather.quelea.velocity.stream;

import com.sun.net.httpserver.HttpServer;
import net.onelitefeather.quelea.velocity.http.HttpQueleaGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventStreamTest {

    private static final long WAIT_SECONDS = 5;

    private HttpServer server;
    private ExecutorService serverThreads;
    private final CountDownLatch hold = new CountDownLatch(1);
    private final BlockingQueue<String> seen = new LinkedBlockingQueue<>();
    private EventStream stream;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        serverThreads = Executors.newCachedThreadPool();
        server.setExecutor(serverThreads);
        server.createContext("/v1/events", exchange -> {
            seen.add("connect:" + exchange.getRequestURI().getQuery());
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                write(out, "id: 11111111-1111-1111-1111-111111111111\nevent: party.created\n"
                        + "data: {\"type\":\"party.created\"}\n\n");
                write(out, ": keep-alive\n\n");
                write(out, "id: 22222222-2222-2222-2222-222222222222\nevent: party.joined\n"
                        + "data: {\"type\":\"party.joined\"}\n\n");
                // Hold the connection open, as the real stream does, until the test is done.
                hold.await(WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stopEverything() {
        hold.countDown();
        if (stream != null) {
            stream.stop();
        }
        server.stop(0);
        serverThreads.shutdownNow();
    }

    private static void write(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private URI eventsUri() {
        return HttpQueleaGateway.eventsUri(
                "http://" + server.getAddress().getAddress().getHostAddress() + ":" + server.getAddress().getPort(),
                "proxy-test");
    }

    private EventStream startStream() {
        stream = new EventStream(
                HttpQueleaGateway.newClient(),
                eventsUri(),
                () -> seen.add("snapshot"),
                message -> seen.add("event:" + message.id() + ":" + message.event()),
                new Backoff(Duration.ofSeconds(1), Duration.ofSeconds(30)),
                delay -> { },
                LoggerFactory.getLogger(EventStreamTest.class));
        stream.start();
        return stream;
    }

    private String next() throws InterruptedException {
        String value = seen.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(value, "expected another entry on the stream within " + WAIT_SECONDS + " s");
        return value;
    }

    @Test
    void sendsTheSnapshotBeforeAnyEventIsRead() throws InterruptedException {
        startStream();

        assertEquals("connect:proxyId=proxy-test", next());
        assertEquals("snapshot", next(), "the snapshot goes out before the first event is read");
        assertEquals("event:11111111-1111-1111-1111-111111111111:party.created", next());
    }

    @Test
    void dispatchesEveryEventAndIgnoresKeepAliveComments() throws InterruptedException {
        startStream();

        assertEquals("connect:proxyId=proxy-test", next());
        assertEquals("snapshot", next());
        assertEquals("event:11111111-1111-1111-1111-111111111111:party.created", next());
        assertEquals("event:22222222-2222-2222-2222-222222222222:party.joined", next());
        assertTrue(seen.isEmpty(), "the comment line produced no message");
    }
}
