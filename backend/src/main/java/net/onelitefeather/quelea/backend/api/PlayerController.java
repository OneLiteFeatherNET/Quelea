package net.onelitefeather.quelea.backend.api;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import net.onelitefeather.quelea.backend.party.Invite;
import net.onelitefeather.quelea.backend.party.Party;
import net.onelitefeather.quelea.backend.party.PartyService;
import net.onelitefeather.quelea.backend.problem.Problem;

import java.util.List;
import java.util.UUID;

@Validated
@Controller("/v1/players")
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
                    content = @Content(schema = @Schema(implementation = Party.class))),
            @ApiResponse(responseCode = "404", description = "The player is not in a party",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class)))
    })
    public Party partyOf(@PathVariable UUID playerId) {
        return metrics.record("party-of", () -> parties.partyOf(playerId));
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
                    content = @Content(schema = @Schema(implementation = Party.class))),
            @ApiResponse(responseCode = "404", description = "There is no live invite to that party",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class))),
            @ApiResponse(responseCode = "409", description = "The player is in a party or the party is full",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = Problem.class)))
    })
    public Party accept(@PathVariable UUID playerId, @PathVariable UUID partyId) {
        return metrics.record("accept", () -> parties.accept(playerId, partyId));
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
