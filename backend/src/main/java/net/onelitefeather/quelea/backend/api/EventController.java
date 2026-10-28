package net.onelitefeather.quelea.backend.api;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.sse.Event;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import net.onelitefeather.quelea.backend.event.EventBroadcaster;
import net.onelitefeather.quelea.backend.event.PartyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * The event stream of one proxy. Each party event is one SSE message named by its type and carrying its id; an idle
 * stream receives a comment line ({@code :keep-alive}) every {@code quelea.events.keepalive}.
 */
@Validated
@Controller("/v1/events")
@Tag(name = "events", description = "Party event stream for the proxies")
public class EventController {

    static final String KEEP_ALIVE_COMMENT = "keep-alive";

    private static final Logger LOG = LoggerFactory.getLogger(EventController.class);

    private final EventBroadcaster broadcaster;
    private final Duration keepAlive;

    public EventController(EventBroadcaster broadcaster,
                           @Property(name = "quelea.events.keepalive", defaultValue = "15s") Duration keepAlive) {
        this.broadcaster = broadcaster;
        this.keepAlive = keepAlive;
    }

    @Get
    @Produces(MediaType.TEXT_EVENT_STREAM)
    @Operation(operationId = "streamEvents", summary = "Open the party event stream of a proxy (server-sent events)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A text/event-stream of PartyEvent messages",
                    content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM,
                            schema = @Schema(implementation = PartyEvent.class))),
            @ApiResponse(responseCode = "400", description = "Missing or invalid proxy id",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = net.onelitefeather.quelea.backend.problem.Problem.class)))
    })
    public Flux<Event<?>> stream(@QueryValue @NotBlank @Size(max = 64) String proxyId) {
        Flux<Event<?>> events = broadcaster.stream().map(EventController::toMessage);
        Flux<Event<?>> comments = Flux.interval(keepAlive).map(tick -> Event.of("").comment(KEEP_ALIVE_COMMENT));
        return Flux.<Event<?>>merge(events, comments)
                .doOnSubscribe(subscription -> LOG.info("Event stream opened for proxy {}", proxyId))
                .doFinally(signal -> LOG.info("Event stream closed for proxy {} ({})", proxyId, signal));
    }

    static Event<?> toMessage(PartyEvent event) {
        return Event.of(event).id(event.id().toString()).name(event.type());
    }
}
