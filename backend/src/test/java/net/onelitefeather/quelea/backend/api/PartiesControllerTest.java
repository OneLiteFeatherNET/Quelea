package net.onelitefeather.quelea.backend.api;

import tools.jackson.databind.JsonNode;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import net.onelitefeather.quelea.backend.support.ApiClient;
import net.onelitefeather.quelea.backend.support.ApiClient.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(rebuildContext = true)
class PartiesControllerTest {

    static final String PROXY = "proxy-parties";

    @Inject
    @Client("/")
    HttpClient client;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(client);
    }

    @Test
    void createAnswers201WithThePartyLedByTheLeader() {
        UUID leader = api.login(PROXY, name("lead"));

        Response response = create(leader);

        assertEquals(201, response.status());
        JsonNode party = response.json();
        assertTrue(party.hasNonNull("id"), "the party needs an id");
        assertEquals(leader.toString(), party.get("leaderId").asText());
        assertEquals(leader.toString(), party.get("members").get(0).get("id").asText(), "the leader is the first member");
        assertTrue(party.get("createdAt").isTextual(), "createdAt is an ISO-8601 string");
    }

    @Test
    void getAnswers200WithTheParty() {
        UUID leader = api.login(PROXY, name("lead"));
        String partyId = create(leader).json().get("id").asText();

        Response response = api.send(HttpRequest.GET("/v1/parties/" + partyId));

        assertEquals(200, response.status());
        assertEquals(partyId, response.json().get("id").asText());
        assertEquals(1, response.json().get("members").size(), "a new party has only its leader");
    }

    @Test
    void inviteAnswers201WithTheInviteAndCreatesTheInviterParty() {
        UUID leader = api.login(PROXY, name("lead"));
        String targetName = name("tgt");
        api.login(PROXY, targetName);

        Response response = invite(leader, targetName);

        assertEquals(201, response.status());
        JsonNode invite = response.json();
        assertEquals(leader.toString(), invite.get("inviterId").asText());
        assertEquals(targetName, invite.get("targetName").asText());
        assertTrue(invite.get("expiresAt").isTextual(), "expiresAt is an ISO-8601 string");
        assertTrue(invite.hasNonNull("partyId") && invite.hasNonNull("targetId") && invite.hasNonNull("inviterName"));
        assertEquals(1, api.send(HttpRequest.GET("/v1/parties/" + invite.get("partyId").asText())).json()
                .get("members").size(), "inviting creates the inviter's party with one member until the invite is accepted");
    }

    @Test
    void kickAnswers200WithThePartyWithoutTheKickedMember() {
        UUID leader = api.login(PROXY, name("lead"));
        String memberName = name("mem");
        UUID member = api.login(PROXY, memberName);
        String partyId = joinedParty(leader, member, memberName);

        Response response = api.postJson("/v1/parties/" + partyId + "/kick",
                "{\"actorId\":\"" + leader + "\",\"targetId\":\"" + member + "\"}");

        assertEquals(200, response.status());
        assertEquals(1, response.json().get("members").size(), "the kicked member must be gone");
    }

    @Test
    void promoteAnswers200WithTheNewLeader() {
        UUID leader = api.login(PROXY, name("lead"));
        String memberName = name("mem");
        UUID member = api.login(PROXY, memberName);
        String partyId = joinedParty(leader, member, memberName);

        Response response = api.postJson("/v1/parties/" + partyId + "/promote",
                "{\"actorId\":\"" + leader + "\",\"targetId\":\"" + member + "\"}");

        assertEquals(200, response.status());
        assertEquals(member.toString(), response.json().get("leaderId").asText());
    }

    @Test
    void disbandAnswers204AndThePartyIsGone() {
        UUID leader = api.login(PROXY, name("lead"));
        String partyId = create(leader).json().get("id").asText();

        Response response = api.postJson("/v1/parties/" + partyId + "/disband", "{\"actorId\":\"" + leader + "\"}");

        assertEquals(204, response.status());
        assertEquals(404, api.send(HttpRequest.GET("/v1/parties/" + partyId)).status(), "a disbanded party must not be found");
    }

    @Test
    void warpAnswers202() {
        UUID leader = api.login(PROXY, name("lead"));
        String partyId = create(leader).json().get("id").asText();

        Response response = api.postJson("/v1/parties/" + partyId + "/warp",
                "{\"actorId\":\"" + leader + "\",\"server\":\"Lobby-1\"}");

        assertEquals(202, response.status());
    }

    @Test
    void chatAnswers202() {
        UUID leader = api.login(PROXY, name("lead"));
        String partyId = create(leader).json().get("id").asText();

        Response response = api.postJson("/v1/parties/" + partyId + "/chat",
                "{\"actorId\":\"" + leader + "\",\"message\":\"hello\"}");

        assertEquals(202, response.status());
    }

    private Response create(UUID leader) {
        return api.postJson("/v1/parties", "{\"leaderId\":\"" + leader + "\"}");
    }

    private Response invite(UUID actor, String targetName) {
        return api.postJson("/v1/parties/invites", "{\"actorId\":\"" + actor + "\",\"targetName\":\"" + targetName + "\"}");
    }

    /** Leader invites the member and the member accepts; returns the party id. */
    private String joinedParty(UUID leader, UUID member, String memberName) {
        String partyId = invite(leader, memberName).json().get("partyId").asText();
        Response accepted = api.send(HttpRequest.POST("/v1/players/" + member + "/invites/" + partyId + "/accept", ""));
        assertEquals(200, accepted.status(), "the setup join must succeed");
        return partyId;
    }

    static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }
}
