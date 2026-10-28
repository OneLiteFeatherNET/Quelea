package net.onelitefeather.quelea.backend.api;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import net.onelitefeather.quelea.backend.party.Invite;
import net.onelitefeather.quelea.backend.party.Party;
import net.onelitefeather.quelea.backend.party.PartyService;
import net.onelitefeather.quelea.backend.party.Precondition;
import net.onelitefeather.quelea.backend.problem.Problem;

import java.util.UUID;
import net.onelitefeather.quelea.backend.security.Roles;

@Validated
// Blocking Dragonfly calls and lock waits: keep them off the Netty event loop.
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/v1/parties")
@Secured(Roles.CLIENT)
@Tag(name = "parties", description = "Party lifecycle and party commands")
public class PartyController {

    private static final String PROBLEM = "application/problem+json";
    private static final String ETAG_DESCRIPTION = "Strong entity tag of the party: its version, for example \"4\"";
    private static final String IF_MATCH_DESCRIPTION = "Optional. Strong entity tag(s) of the party the client read, or *. "
            + "The command runs only if one matches the current version; otherwise it answers 412 precondition-failed.";

    private final PartyService parties;
    private final OperationMetrics metrics;

    public PartyController(PartyService parties, OperationMetrics metrics) {
        this.parties = parties;
        this.metrics = metrics;
    }

    @Post
    @Status(HttpStatus.CREATED)
    @Operation(operationId = "createParty", summary = "Create a party led by the given player")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The new party, at version 1",
                    headers = @io.swagger.v3.oas.annotations.headers.Header(name = HttpHeaders.ETAG, description = ETAG_DESCRIPTION,
                            schema = @Schema(type = "string")),
                    content = @Content(schema = @Schema(implementation = Party.class))),
            @ApiResponse(responseCode = "400", description = "Invalid body",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "404", description = "Player is not online",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "409", description = "Player is already in a party",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class)))
    })
    public HttpResponse<Party> create(@Body @Valid CreatePartyRequest request) {
        return metrics.record("create", () -> PartyResponses.created(parties.create(request.leaderId())));
    }

    @Get("/{partyId}")
    @Operation(operationId = "getParty", summary = "Get a party by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The party",
                    headers = @io.swagger.v3.oas.annotations.headers.Header(name = HttpHeaders.ETAG, description = ETAG_DESCRIPTION,
                            schema = @Schema(type = "string")),
                    content = @Content(schema = @Schema(implementation = Party.class))),
            @ApiResponse(responseCode = "304", description = "If-None-Match named the current entity tag; no body"),
            @ApiResponse(responseCode = "404", description = "The party does not exist",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class)))
    })
    public HttpResponse<Party> get(@PathVariable UUID partyId,
                                   @Parameter(in = ParameterIn.HEADER, name = HttpHeaders.IF_NONE_MATCH,
                                           description = "Optional. Entity tag(s) the client holds; a current one answers 304.")
                                   @Nullable @Header(HttpHeaders.IF_NONE_MATCH) String ifNoneMatch) {
        return metrics.record("get", () -> PartyResponses.read(parties.get(partyId), ifNoneMatch));
    }

    @Post("/invites")
    @Status(HttpStatus.CREATED)
    @Operation(operationId = "invite", summary = "Invite an online player by name; creates the inviter's party if needed")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The invite",
                    content = @Content(schema = @Schema(implementation = Invite.class))),
            @ApiResponse(responseCode = "400", description = "Invalid body or inviting yourself",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "403", description = "Only the leader may invite",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "404", description = "Actor or target is not online",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "409", description = "Target is in a party or the party is full",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class)))
    })
    public Invite invite(@Body @Valid InviteRequest request) {
        return metrics.record("invite", () -> parties.invite(request.actorId(), request.targetName()));
    }

    @Post("/{partyId}/kick")
    @Operation(operationId = "kick", summary = "Remove a member from the party (leader only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The party after the kick",
                    headers = @io.swagger.v3.oas.annotations.headers.Header(name = HttpHeaders.ETAG, description = ETAG_DESCRIPTION,
                            schema = @Schema(type = "string")),
                    content = @Content(schema = @Schema(implementation = Party.class))),
            @ApiResponse(responseCode = "400", description = "Invalid body",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "403", description = "Not the leader, or target is not a member",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "404", description = "The party does not exist",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "412", description = "If-Match names another version; the party is unchanged",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class)))
    })
    public HttpResponse<Party> kick(@PathVariable UUID partyId, @Body @Valid ActorTargetRequest request,
                                    @Parameter(in = ParameterIn.HEADER, name = HttpHeaders.IF_MATCH, description = IF_MATCH_DESCRIPTION)
                                    @Nullable @Header(HttpHeaders.IF_MATCH) String ifMatch) {
        return metrics.record("kick", () -> PartyResponses.ok(
                parties.kick(partyId, request.actorId(), request.targetId(), new Precondition(ifMatch))));
    }

    @Post("/{partyId}/promote")
    @Operation(operationId = "promote", summary = "Make a member the leader (leader only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The party after the promotion",
                    headers = @io.swagger.v3.oas.annotations.headers.Header(name = HttpHeaders.ETAG, description = ETAG_DESCRIPTION,
                            schema = @Schema(type = "string")),
                    content = @Content(schema = @Schema(implementation = Party.class))),
            @ApiResponse(responseCode = "400", description = "Invalid body or promoting yourself",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "403", description = "Not the leader, or target is not a member",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "404", description = "The party does not exist",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "412", description = "If-Match names another version; the party is unchanged",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class)))
    })
    public HttpResponse<Party> promote(@PathVariable UUID partyId, @Body @Valid ActorTargetRequest request,
                                       @Parameter(in = ParameterIn.HEADER, name = HttpHeaders.IF_MATCH, description = IF_MATCH_DESCRIPTION)
                                       @Nullable @Header(HttpHeaders.IF_MATCH) String ifMatch) {
        return metrics.record("promote", () -> PartyResponses.ok(
                parties.promote(partyId, request.actorId(), request.targetId(), new Precondition(ifMatch))));
    }

    @Post("/{partyId}/disband")
    @Status(HttpStatus.NO_CONTENT)
    @Operation(operationId = "disband", summary = "Disband the party (leader only)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The party was disbanded"),
            @ApiResponse(responseCode = "400", description = "Invalid body",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "403", description = "Not the leader",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "404", description = "The party does not exist",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "412", description = "If-Match names another version; the party is unchanged",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class)))
    })
    public void disband(@PathVariable UUID partyId, @Body @Valid ActorRequest request,
                        @Parameter(in = ParameterIn.HEADER, name = HttpHeaders.IF_MATCH, description = IF_MATCH_DESCRIPTION)
                        @Nullable @Header(HttpHeaders.IF_MATCH) String ifMatch) {
        metrics.run("disband", () -> parties.disband(partyId, request.actorId(), new Precondition(ifMatch)));
    }

    @Post("/{partyId}/warp")
    @Status(HttpStatus.ACCEPTED)
    @Operation(operationId = "warp", summary = "Send the party to a server (leader only)")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "The warp was published to the proxies"),
            @ApiResponse(responseCode = "400", description = "Invalid body",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "403", description = "Not the leader",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "404", description = "The party does not exist",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "412", description = "If-Match names another version; nothing is published",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class)))
    })
    public void warp(@PathVariable UUID partyId, @Body @Valid WarpRequest request,
                     @Parameter(in = ParameterIn.HEADER, name = HttpHeaders.IF_MATCH, description = IF_MATCH_DESCRIPTION)
                     @Nullable @Header(HttpHeaders.IF_MATCH) String ifMatch) {
        metrics.run("warp", () -> parties.warp(partyId, request.actorId(), request.server(), request.fromServer(),
                new Precondition(ifMatch)));
    }

    @Post("/{partyId}/chat")
    @Status(HttpStatus.ACCEPTED)
    @Operation(operationId = "chat", summary = "Send a chat message to the party (members only)")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "The message was published to the proxies"),
            @ApiResponse(responseCode = "400", description = "Invalid body or message length",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "403", description = "Not a member of the party",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "404", description = "The party does not exist",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class)))
    })
    public void chat(@PathVariable UUID partyId, @Body @Valid ChatRequest request) {
        metrics.run("chat", () -> parties.chat(partyId, request.actorId(), request.message()));
    }
}
