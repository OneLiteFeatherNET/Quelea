package net.onelitefeather.quelea.velocity.stream;

import net.onelitefeather.quelea.velocity.auth.ClientCredentialsTokenProvider;
import net.onelitefeather.quelea.velocity.http.QueleaUnavailableException;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Keeps one {@code GET /v1/events} stream open on a virtual thread. After every successful connect it runs
 * {@code onConnect} (the presence snapshot) before reading events; on failure it waits with {@link Backoff} and
 * reconnects until {@link #stop()} is called. Each connect carries the bearer token; a 401 discards that token, so
 * the reconnect after the backoff fetches a new one.
 */
public final class EventStream {

    private final HttpClient client;
    private final URI uri;
    private final Optional<ClientCredentialsTokenProvider> tokens;
    private final Runnable onConnect;
    private final Runnable onDisconnect;
    private final Consumer<SseMessage> onMessage;
    private final Backoff backoff;
    private final Sleeper sleeper;
    private final Logger logger;

    private volatile boolean running;
    private volatile Thread thread;
    private volatile Stream<String> body;

    public EventStream(HttpClient client, URI uri, Optional<ClientCredentialsTokenProvider> tokens,
                       Runnable onConnect, Runnable onDisconnect, Consumer<SseMessage> onMessage,
                       Backoff backoff, Sleeper sleeper, Logger logger) {
        this.client = client;
        this.uri = uri;
        this.tokens = tokens;
        this.onConnect = onConnect;
        this.onDisconnect = onDisconnect;
        this.onMessage = onMessage;
        this.backoff = backoff;
        this.sleeper = sleeper;
        this.logger = logger;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        thread = Thread.ofVirtual().name("quelea-events").start(this::run);
    }

    /**
     * Stops reading and reconnecting. Does not wait for the reader thread.
     */
    public void stop() {
        running = false;
        Stream<String> current = body;
        if (current != null) {
            current.close();
        }
        Thread reader = thread;
        if (reader != null) {
            reader.interrupt();
        }
    }

    private void run() {
        while (running) {
            try {
                connectAndRead();
            } catch (QueleaUnavailableException | IOException | UncheckedIOException e) {
                logger.warn("Event stream failed: {}", e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                // E.g. a rejected snapshot: the stream must reconnect, never end the reader thread.
                logger.error("Event stream failed unexpectedly", e);
            }
            if (!running) {
                return;
            }
            onDisconnect.run();
            try {
                Duration delay = backoff.nextDelay();
                logger.info("Reconnecting the event stream in {}", delay);
                sleeper.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void connectAndRead() throws IOException, InterruptedException {
        Optional<String> token = tokens.isPresent() ? Optional.of(tokens.get().token()) : Optional.empty();
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .header("Accept", "text/event-stream")
                .GET();
        token.ifPresent(value -> request.header("Authorization", "Bearer " + value));
        HttpResponse<Stream<String>> response = client.send(request.build(), HttpResponse.BodyHandlers.ofLines());
        try (Stream<String> lines = response.body()) {
            body = lines;
            if (response.statusCode() != 200) {
                if (response.statusCode() == 401 && token.isPresent()) {
                    tokens.get().rejected(token.get());
                }
                throw new QueleaUnavailableException("event stream answered HTTP " + response.statusCode());
            }
            onConnect.run();
            backoff.reset();
            logger.info("Event stream connected");
            SseParser parser = new SseParser(this::dispatch);
            Iterator<String> iterator = lines.iterator();
            while (running && iterator.hasNext()) {
                parser.accept(iterator.next());
            }
        } finally {
            body = null;
        }
    }

    private void dispatch(SseMessage message) {
        try {
            onMessage.accept(message);
        } catch (RuntimeException e) {
            // One bad event must not end the stream.
            logger.error("Could not handle event {}", message.id(), e);
        }
    }
}
