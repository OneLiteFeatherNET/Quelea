package net.onelitefeather.quelea.backend.api;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import net.onelitefeather.quelea.backend.problem.Problem;
import net.onelitefeather.quelea.backend.support.ApiClient;
import net.onelitefeather.quelea.backend.support.ApiClient.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each domain rule answers with its status and a problem document whose code is the stable slug. */
@MicronautTest(rebuildContext = true)
@Property(name = "quelea.party.max-size", value = "2")
class PartyProblemsTest {

    static final String PROXY = "proxy-problems";
    static final String PROBLEM_JSON = "application/problem+json";

    @Inject
    @Client("/")
    HttpClient client;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(client);
    }

    @Test
    void notLeaderAnswers403AsProblemJson() {
        UUID leader = api.login(PROXY, name("lead"));
        String memberName = name("mem");
        UUID member = api.login(PROXY, memberName);
        String partyId = joinedParty(leader, member, memberName);

        Response response = api.postJson("/v1/parties/" + partyId + "/kick",
                "{\"actorId\":\"" + member + "\",\"targetId\":\"" + leader + "\"}");

        assertEquals(403, response.status(), "a member may not kick");
        assertTrue(response.contentType().startsWith(PROBLEM_JSON), "the content type must be " + PROBLEM_JSON);
        assertEquals("not-leader", response.problemCode());
        assertEquals("https://github.com/OneLiteFeatherNET/Quelea/blob/main/docs/problems.md#not-leader",
                response.json().get("type").asText(), "the type must link to the docs section of the code");
    }

    @Test
    void alreadyInPartyAnswers409() {
        UUID leader = api.login(PROXY, name("lead"));
        api.postJson("/v1/parties", "{\"leaderId\":\"" + leader + "\"}");

        Response response = api.postJson("/v1/parties", "{\"leaderId\":\"" + leader + "\"}");

        assertEquals(409, response.status());
        assertEquals("already-in-party", response.problemCode());
    }

    @Test
    void playerNotOnlineAnswers404() {
        UUID leader = api.login(PROXY, name("lead"));

        Response response = invite(leader, name("nobody"));

        assertEquals(404, response.status());
        assertEquals("player-not-online", response.problemCode());
    }

    @Test
    void emptyChatAnswers400ValidationFailed() {
        UUID leader = api.login(PROXY, name("lead"));
        String partyId = api.postJson("/v1/parties", "{\"leaderId\":\"" + leader + "\"}").json().get("id").asText();

        Response response = api.postJson("/v1/parties/" + partyId + "/chat",
                "{\"actorId\":\"" + leader + "\",\"message\":\"\"}");

        assertEquals(400, response.status());
        assertEquals("validation-failed", response.problemCode());
        assertTrue(response.json().get("errors").toString().contains("message"),
                "the errors must name the field that failed");
    }

    @Test
    void tooLongTargetNameAnswers400ValidationFailed() {
        UUID leader = api.login(PROXY, name("lead"));

        Response response = invite(leader, "a-name-over-sixteen-characters");

        assertEquals(400, response.status(), "a player name longer than 16 characters cannot exist");
        assertEquals("validation-failed", response.problemCode());
    }

    @Test
    void partyFullAnswers409() {
        UUID leader = api.login(PROXY, name("lead"));
        String memberName = name("mem");
        UUID member = api.login(PROXY, memberName);
        joinedParty(leader, member, memberName);
        String thirdName = name("third");
        api.login(PROXY, thirdName);

        Response response = invite(leader, thirdName);

        assertEquals(409, response.status(), "the party holds two members, so a third is refused");
        assertEquals("party-full", response.problemCode());
    }

    @Test
    void problemDocumentOfUnknownPartyIsNotFound() {
        Response response = api.send(HttpRequest.GET("/v1/parties/" + UUID.randomUUID()));

        assertEquals(404, response.status());
        assertEquals("party-not-found", response.problemCode());
        assertEquals(Problem.MEDIA_TYPE, response.contentType().split(";")[0].trim());
    }

    private Response invite(UUID actor, String targetName) {
        return api.postJson("/v1/parties/invites", "{\"actorId\":\"" + actor + "\",\"targetName\":\"" + targetName + "\"}");
    }

    private String joinedParty(UUID leader, UUID member, String memberName) {
        String partyId = invite(leader, memberName).json().get("partyId").asText();
        api.send(HttpRequest.POST("/v1/players/" + member + "/invites/" + partyId + "/accept", ""));
        return partyId;
    }

    static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }
}
