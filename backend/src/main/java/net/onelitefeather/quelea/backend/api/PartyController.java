package net.onelitefeather.quelea.backend.api;

import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import io.micronaut.security.annotation.Secured;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import net.onelitefeather.quelea.backend.party.Invite;
import net.onelitefeather.quelea.backend.party.Party;
import net.onelitefeather.quelea.backend.party.PartyService;
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
            @ApiResponse(responseCode = "201", description = "The new party",
                    content = @Content(schema = @Schema(implementation = Party.class))),
            @ApiResponse(responseCode = "400", description = "Invalid body",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "404", description = "Player is not online",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "409", description = "Player is already in a party",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class)))
    })
    public Party create(@Body @Valid CreatePartyRequest request) {
        return metrics.record("create", () -> parties.create(request.leaderId()));
    }

    @Get("/{partyId}")
    @Operation(operationId = "getParty", summary = "Get a party by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The party",
                    content = @Content(schema = @Schema(implementation = Party.class))),
            @ApiResponse(responseCode = "404", description = "The party does not exist",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class)))
    })
    public Party get(@PathVariable UUID partyId) {
        return metrics.record("get", () -> parties.get(partyId));
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
                    content = @Content(schema = @Schema(implementation = Party.class))),
            @ApiResponse(responseCode = "400", description = "Invalid body",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "403", description = "Not the leader, or target is not a member",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "404", description = "The party does not exist",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class)))
    })
    public Party kick(@PathVariable UUID partyId, @Body @Valid ActorTargetRequest request) {
        return metrics.record("kick", () -> parties.kick(partyId, request.actorId(), request.targetId()));
    }

    @Post("/{partyId}/promote")
    @Operation(operationId = "promote", summary = "Make a member the leader (leader only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The party after the promotion",
                    content = @Content(schema = @Schema(implementation = Party.class))),
            @ApiResponse(responseCode = "400", description = "Invalid body or promoting yourself",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "403", description = "Not the leader, or target is not a member",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "404", description = "The party does not exist",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class)))
    })
    public Party promote(@PathVariable UUID partyId, @Body @Valid ActorTargetRequest request) {
        return metrics.record("promote", () -> parties.promote(partyId, request.actorId(), request.targetId()));
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
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class)))
    })
    public void disband(@PathVariable UUID partyId, @Body @Valid ActorRequest request) {
        metrics.run("disband", () -> parties.disband(partyId, request.actorId()));
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
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class)))
    })
    public void warp(@PathVariable UUID partyId, @Body @Valid WarpRequest request) {
        metrics.run("warp", () -> parties.warp(partyId, request.actorId(), request.server()));
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
