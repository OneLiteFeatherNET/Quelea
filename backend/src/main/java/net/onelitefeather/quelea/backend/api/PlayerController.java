package net.onelitefeather.quelea.backend.api;

import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import io.micronaut.security.annotation.Secured;
import io.micronaut.validation.Validated;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import net.onelitefeather.quelea.backend.party.Invite;
import net.onelitefeather.quelea.backend.party.JoinOutcome;
import net.onelitefeather.quelea.backend.party.JoinRequest;
import net.onelitefeather.quelea.backend.party.Party;
import net.onelitefeather.quelea.backend.party.PartyService;
import net.onelitefeather.quelea.backend.problem.Problem;

import java.util.List;
import java.util.UUID;
import net.onelitefeather.quelea.backend.security.Roles;

@Validated
// Blocking Dragonfly calls and lock waits: keep them off the Netty event loop.
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/v1/players")
@Secured(Roles.CLIENT)
@Tag(name = "players", description = "A player's party membership and invites")
public class PlayerController {

    private static final String PROBLEM = "application/problem+json";

    private final PartyService parties;
    private final OperationMetrics metrics;

    public PlayerController(PartyService parties, OperationMetrics metrics) {
        this.parties = parties;
        this.metrics = metrics;
    }

    @Get("/{playerId}/party")
    @Operation(operationId = "getPartyOfPlayer", summary = "Get the party a player is in")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The player's party",
                    headers = @io.swagger.v3.oas.annotations.headers.Header(name = HttpHeaders.ETAG,
                            description = "Strong entity tag of the party: its version, for example \"4\"",
                            schema = @Schema(type = "string")),
                    content = @Content(schema = @Schema(implementation = Party.class))),
            @ApiResponse(responseCode = "304", description = "If-None-Match named the current entity tag; no body"),
            @ApiResponse(responseCode = "404", description = "The player is not in a party",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class)))
    })
    public HttpResponse<Party> partyOf(@PathVariable UUID playerId,
                                       @Parameter(in = ParameterIn.HEADER, name = HttpHeaders.IF_NONE_MATCH,
                                               description = "Optional. Entity tag(s) the client holds; a current one answers 304.")
                                       @Nullable @Header(HttpHeaders.IF_NONE_MATCH) String ifNoneMatch) {
        return metrics.record("party-of", () -> PartyResponses.read(parties.partyOf(playerId), ifNoneMatch));
    }

    @Get("/{playerId}/invites")
    @Operation(operationId = "listInvites", summary = "List the live invites of a player")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The live invites, possibly empty",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = Invite.class))))
    })
    public List<Invite> invites(@PathVariable UUID playerId) {
        return metrics.record("invites", () -> parties.invitesOf(playerId));
    }

    @Post("/{playerId}/invites/{partyId}/accept")
    @Operation(operationId = "acceptInvite", summary = "Accept an invite and join the party")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The party after the join",
                    headers = @io.swagger.v3.oas.annotations.headers.Header(name = HttpHeaders.ETAG,
                            description = "Strong entity tag of the party: its version, for example \"4\"",
                            schema = @Schema(type = "string")),
                    content = @Content(schema = @Schema(implementation = Party.class))),
            @ApiResponse(responseCode = "404", description = "There is no live invite to that party",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "409", description = "The player is in a party or the party is full",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class)))
    })
    public HttpResponse<Party> accept(@PathVariable UUID playerId, @PathVariable UUID partyId) {
        return metrics.record("accept", () -> PartyResponses.ok(parties.accept(playerId, partyId)));
    }

    @Post("/{playerId}/invites/{partyId}/deny")
    @Status(HttpStatus.NO_CONTENT)
    @Operation(operationId = "denyInvite", summary = "Deny an invite")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The invite was dropped"),
            @ApiResponse(responseCode = "404", description = "There is no live invite to that party",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class)))
    })
    public void deny(@PathVariable UUID playerId, @PathVariable UUID partyId) {
        metrics.run("deny", () -> parties.deny(playerId, partyId));
    }

    @Post("/{playerId}/party/join")
    @Operation(operationId = "joinParty", summary = "Join the public party of an online player by name")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The party after the join",
                    headers = @io.swagger.v3.oas.annotations.headers.Header(name = HttpHeaders.ETAG,
                            description = "Strong entity tag of the party: its version, for example \"4\"",
                            schema = @Schema(type = "string")),
                    content = @Content(schema = @Schema(implementation = Party.class))),
            @ApiResponse(responseCode = "403", description = "The player was removed from the party",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "404", description = "The player is offline, the target is not in a party, or the player is not online",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "409", description = "The player is in a party, the party is not public, or it is full",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class)))
    })
    public HttpResponse<Party> join(@PathVariable UUID playerId, @Body @Valid TargetNameRequest request) {
        return metrics.record("join", () -> PartyResponses.ok(parties.join(playerId, request.targetName())));
    }

    @Post("/{playerId}/party/requests")
    @Operation(operationId = "requestJoin", summary = "Ask the party of an online player to let you in; a public party admits you at once")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The party is public: the player joined it directly",
                    headers = @io.swagger.v3.oas.annotations.headers.Header(name = HttpHeaders.ETAG,
                            description = "Strong entity tag of the party: its version, for example \"4\"",
                            schema = @Schema(type = "string")),
                    content = @Content(schema = @Schema(implementation = Party.class))),
            @ApiResponse(responseCode = "201", description = "The party is private: the request is open until its expiry",
                    content = @Content(schema = @Schema(implementation = JoinRequest.class))),
            @ApiResponse(responseCode = "403", description = "The player was removed from the party",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "404", description = "The player is offline, or the target is not in a party",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "409", description = "The player is in a party, a request is already open, or the party is full",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "429", description = "The player asked too recently; Retry-After gives the seconds left",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class)))
    })
    public HttpResponse<?> requestJoin(@PathVariable UUID playerId, @Body @Valid TargetNameRequest request) {
        return metrics.record("request", () -> joinOutcome(parties.requestJoin(playerId, request.targetName())));
    }

    private static HttpResponse<?> joinOutcome(JoinOutcome outcome) {
        return switch (outcome) {
            case JoinOutcome.Joined joined -> PartyResponses.ok(joined.party());
            case JoinOutcome.Requested requested -> HttpResponse.created(requested.request());
        };
    }

    @Post("/{playerId}/party/leave")
    @Status(HttpStatus.NO_CONTENT)
    @Operation(operationId = "leaveParty", summary = "Leave the party the player is in")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The player left the party"),
            @ApiResponse(responseCode = "404", description = "The player is not in a party",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class)))
    })
    public void leave(@PathVariable UUID playerId) {
        metrics.run("leave", () -> parties.leave(playerId, "leave"));
    }
}
