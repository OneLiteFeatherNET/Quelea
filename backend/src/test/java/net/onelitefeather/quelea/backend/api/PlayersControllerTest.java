package net.onelitefeather.quelea.backend.api;

import tools.jackson.databind.JsonNode;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import net.onelitefeather.quelea.backend.security.TestJwtIssuer;
import net.onelitefeather.quelea.backend.support.ApiClient;
import net.onelitefeather.quelea.backend.support.ApiClient.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(rebuildContext = true)
class PlayersControllerTest {

    static final String PROXY = "proxy-players";

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    TestJwtIssuer jwt;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(client, jwt);
    }

    @Test
    void partyOfPlayerAnswers200WithThePlayersParty() {
        UUID leader = api.login(PROXY, name("lead"));
        String partyId = api.postJson("/v1/parties", "{\"leaderId\":\"" + leader + "\"}").json().get("id").asText();

        Response response = api.send(HttpRequest.GET("/v1/players/" + leader + "/party"));

        assertEquals(200, response.status());
        assertEquals(partyId, response.json().get("id").asText());
    }

    @Test
    void invitesAnswers200WithTheLiveInvitesOfThePlayer() {
        UUID leader = api.login(PROXY, name("lead"));
        String targetName = name("tgt");
        UUID target = api.login(PROXY, targetName);
        String partyId = invite(leader, targetName).json().get("partyId").asText();

        Response response = api.send(HttpRequest.GET("/v1/players/" + target + "/invites"));

        assertEquals(200, response.status());
        JsonNode invites = response.json();
        assertTrue(invites.isArray() && invites.size() == 1, "one live invite is listed");
        JsonNode invite = invites.get(0);
        assertEquals(partyId, invite.get("partyId").asText());
        assertEquals(leader.toString(), invite.get("inviterId").asText());
        assertEquals(target.toString(), invite.get("targetId").asText());
        assertEquals(targetName, invite.get("targetName").asText());
    }

    @Test
    void acceptAnswers200WithThePartyAfterTheJoin() {
        UUID leader = api.login(PROXY, name("lead"));
        String targetName = name("tgt");
        UUID target = api.login(PROXY, targetName);
        String partyId = invite(leader, targetName).json().get("partyId").asText();

        Response response = api.send(HttpRequest.POST("/v1/players/" + target + "/invites/" + partyId + "/accept", ""));

        assertEquals(200, response.status());
        assertEquals(2, response.json().get("members").size(), "the accepted player is a member");
    }

    @Test
    void denyAnswers204AndDropsTheInvite() {
        UUID leader = api.login(PROXY, name("lead"));
        String targetName = name("tgt");
        UUID target = api.login(PROXY, targetName);
        String partyId = invite(leader, targetName).json().get("partyId").asText();

        Response response = api.send(HttpRequest.POST("/v1/players/" + target + "/invites/" + partyId + "/deny", ""));

        assertEquals(204, response.status());
        assertEquals(0, api.send(HttpRequest.GET("/v1/players/" + target + "/invites")).json().size(),
                "a denied invite must no longer be listed");
    }

    @Test
    void leaveAnswers204AndTheMemberIsRemoved() {
        UUID leader = api.login(PROXY, name("lead"));
        String targetName = name("tgt");
        UUID target = api.login(PROXY, targetName);
        String partyId = invite(leader, targetName).json().get("partyId").asText();
        api.send(HttpRequest.POST("/v1/players/" + target + "/invites/" + partyId + "/accept", ""));

        Response response = api.send(HttpRequest.POST("/v1/players/" + target + "/party/leave", ""));

        assertEquals(204, response.status());
        Response afterLeave = api.send(HttpRequest.GET("/v1/players/" + target + "/party"));
        assertEquals(404, afterLeave.status(), "the player is no longer in any party");
        assertEquals("not-in-party", afterLeave.problemCode());
    }

    private Response invite(UUID actor, String targetName) {
        return api.postJson("/v1/parties/invites", "{\"actorId\":\"" + actor + "\",\"targetName\":\"" + targetName + "\"}");
    }

    static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }
}
