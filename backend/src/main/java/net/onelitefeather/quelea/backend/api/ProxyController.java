package net.onelitefeather.quelea.backend.api;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.Status;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import net.onelitefeather.quelea.backend.presence.PlayerRef;
import net.onelitefeather.quelea.backend.presence.PresenceService;
import net.onelitefeather.quelea.backend.problem.Problem;

import java.util.List;
import java.util.UUID;

@Validated
@Controller("/v1/proxies")
@Tag(name = "proxies", description = "Presence reported by the Velocity proxies")
public class ProxyController {

    private static final String PROBLEM = "application/problem+json";

    private final PresenceService presence;
    private final OperationMetrics metrics;

    public ProxyController(PresenceService presence, OperationMetrics metrics) {
        this.presence = presence;
        this.metrics = metrics;
    }

    @Put("/{proxyId}/players")
    @Status(HttpStatus.NO_CONTENT)
    @Operation(operationId = "replaceProxyPlayers", summary = "Replace the proxy's player set with a full snapshot")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The snapshot was applied"),
            @ApiResponse(responseCode = "400", description = "Invalid proxy id or player list",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class)))
    })
    public void snapshot(@PathVariable @Size(max = 64) String proxyId,
                         @Body @Valid List<@Valid PlayerRefRequest> players) {
        List<PlayerRef> refs = players.stream().map(player -> new PlayerRef(player.id(), player.name())).toList();
        metrics.run("snapshot", () -> presence.snapshot(proxyId, refs));
    }

    @Put("/{proxyId}/players/{playerId}")
    @Status(HttpStatus.NO_CONTENT)
    @Operation(operationId = "playerLogin", summary = "Record that a player connected to the proxy")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The login was recorded"),
            @ApiResponse(responseCode = "400", description = "Invalid body or proxy id",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class)))
    })
    public void login(@PathVariable @Size(max = 64) String proxyId, @PathVariable UUID playerId,
                      @Body @Valid LoginRequest request) {
        metrics.run("login", () -> presence.login(proxyId, playerId, request.name()));
    }

    @Delete("/{proxyId}/players/{playerId}")
    @Status(HttpStatus.NO_CONTENT)
    @Operation(operationId = "playerDisconnect", summary = "Record that a player disconnected from the proxy")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The disconnect was recorded"),
            @ApiResponse(responseCode = "400", description = "Invalid proxy id",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class)))
    })
    public void disconnect(@PathVariable @Size(max = 64) String proxyId, @PathVariable UUID playerId) {
        metrics.run("disconnect", () -> presence.disconnect(proxyId, playerId));
    }

    @Put("/{proxyId}/heartbeat")
    @Status(HttpStatus.NO_CONTENT)
    @Operation(operationId = "proxyHeartbeat", summary = "Keep the proxy alive")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The heartbeat was recorded"),
            @ApiResponse(responseCode = "400", description = "Invalid proxy id",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class)))
    })
    public void heartbeat(@PathVariable @Size(max = 64) String proxyId) {
        metrics.run("heartbeat", () -> presence.heartbeat(proxyId));
    }
}
