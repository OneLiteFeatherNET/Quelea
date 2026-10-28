package net.onelitefeather.quelea.backend.api;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.sse.Event;
import io.micronaut.security.annotation.Secured;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import net.onelitefeather.quelea.backend.event.EventBroadcaster;
import net.onelitefeather.quelea.backend.event.PartyEvent;
import net.onelitefeather.quelea.backend.security.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;

/**
 * The event stream of one client (a proxy, a lobby server or a tool). Each party event is one SSE message named by its
 * type and carrying its id; an idle stream receives a comment line ({@code :keep-alive}) every
 * {@code quelea.events.keepalive}. A client that falls behind its buffer ({@code quelea.events.buffer}) gets its stream
 * closed and must reconnect.
 */
@Validated
@Controller("/v1/events")
@Secured(Roles.CLIENT)
@Tag(name = "events", description = "Party event stream for the proxies")
public class EventController {

    static final String KEEP_ALIVE_COMMENT = "keep-alive";
    static final String UNLABELLED = "unlabelled";

    private static final Logger LOG = LoggerFactory.getLogger(EventController.class);

    private final EventBroadcaster broadcaster;
    private final Duration keepAlive;
    private final Counter overflows;

    public EventController(EventBroadcaster broadcaster,
                           @Property(name = "quelea.events.keepalive", defaultValue = "15s") Duration keepAlive,
                           MeterRegistry registry) {
        this.broadcaster = broadcaster;
        this.keepAlive = keepAlive;
        this.overflows = Counter.builder("quelea.sse.disconnects")
                .description("Event streams closed by the service, by reason")
                .tag("reason", "overflow")
                .register(registry);
    }

    @Get
    @Produces(MediaType.TEXT_EVENT_STREAM)
    @Operation(operationId = "streamEvents", summary = "Open the party event stream of a client (server-sent events)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A text/event-stream of PartyEvent messages",
                    content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM,
                            schema = @Schema(implementation = PartyEvent.class))),
            @ApiResponse(responseCode = "400", description = "Invalid client label",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = net.onelitefeather.quelea.backend.problem.Problem.class)))
    })
    public Flux<Event<?>> stream(
            @Parameter(description = "Label of the connecting client, only used in logs. Max 64 characters.")
            @Nullable @QueryValue @Size(max = 64) String clientId,
            @Parameter(description = "Alias of clientId, kept for the 0.1.0 proxy plugin. Only used in logs.", deprecated = true)
            @Nullable @QueryValue @Size(max = 64) String proxyId) {
        String client = label(clientId, proxyId);
        // Completes when the overflow ends the event messages, which also ends the keep-alives.
        Sinks.Empty<Void> ended = Sinks.empty();
        Flux<Event<?>> events = broadcaster.stream()
                .map(EventController::toMessage)
                .onErrorResume(Exceptions::isOverflow, overflow -> {
                    LOG.warn("Event stream closed for client {}: it fell behind its event buffer and must reconnect", client);
                    overflows.increment();
                    return Flux.empty();
                })
                .doFinally(signal -> ended.tryEmitEmpty());
        // A keep-alive that cannot be written is dropped: a missed comment must not end the stream.
        Flux<Event<?>> comments = Flux.interval(keepAlive)
                .onBackpressureDrop()
                .map(tick -> Event.of("").comment(KEEP_ALIVE_COMMENT));
        return Flux.<Event<?>>merge(events, comments)
                .takeUntilOther(ended.asMono().thenReturn(true))
                .doOnSubscribe(subscription -> LOG.info("Event stream opened for client {}", client))
                .doFinally(signal -> LOG.info("Event stream closed for client {} ({})", client, signal));
    }

    /** The clientId label; proxyId is the alias of the 0.1.0 plugin. */
    static String label(String clientId, String proxyId) {
        if (clientId != null) {
            return clientId;
        }
        return proxyId != null ? proxyId : UNLABELLED;
    }

    static Event<?> toMessage(PartyEvent event) {
        return Event.of(event).id(event.id().toString()).name(event.type());
    }
}
