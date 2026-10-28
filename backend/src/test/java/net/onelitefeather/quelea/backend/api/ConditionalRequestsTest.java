package net.onelitefeather.quelea.backend.api;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
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

/** Strong entity tags on party reads, If-None-Match on reads and If-Match on leader commands, over HTTP. */
@MicronautTest
class ConditionalRequestsTest {

    static final String PROXY = "proxy-conditional";

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
    void readCarriesTheStrongEtagOfTheVersion() {
        UUID leader = api.login(PROXY, name("lead"));
        String partyId = create(leader).json().get("id").asText();

        Response response = api.send(HttpRequest.GET("/v1/parties/" + partyId));

        assertEquals("\"1\"", response.etag(), "a new party is read at version 1");
    }

    @Test
    void currentIfNoneMatchAnswers304WithoutBody() {
        UUID leader = api.login(PROXY, name("lead"));
        String partyId = create(leader).json().get("id").asText();

        Response response = api.send(HttpRequest.GET("/v1/parties/" + partyId).header("If-None-Match", "\"1\""));

        assertEquals(304, response.status(), "the party is unchanged, so the client's copy is current");
        assertEquals("", response.body(), "a 304 carries no body");
        assertEquals("\"1\"", response.etag(), "a 304 repeats the current entity tag");
    }

    @Test
    void playerPartyReadAlsoAnswers304() {
        UUID leader = api.login(PROXY, name("lead"));
        create(leader);

        Response response = api.send(HttpRequest.GET("/v1/players/" + leader + "/party").header("If-None-Match", "*"));

        assertEquals(304, response.status(), "If-None-Match: * matches the party the player is in");
    }

    @Test
    void staleIfMatchAnswers412AndKeepsTheMember() {
        UUID leader = api.login(PROXY, name("lead"));
        String memberName = name("mem");
        UUID member = api.login(PROXY, memberName);
        String partyId = joinedParty(leader, member, memberName);

        Response response = api.send(kick(partyId, leader, member).header("If-Match", "\"1\""));

        assertEquals(412, response.status(), "the party is at version 2, so If-Match \"1\" is stale");
        assertEquals("precondition-failed", response.problemCode());
        assertEquals(2, api.send(HttpRequest.GET("/v1/parties/" + partyId)).json().get("members").size(),
                "a rejected kick must leave the member in the party");
    }

    @Test
    void currentIfMatchKicksAndAnswersTheNewEtag() {
        UUID leader = api.login(PROXY, name("lead"));
        String memberName = name("mem");
        UUID member = api.login(PROXY, memberName);
        String partyId = joinedParty(leader, member, memberName);

        Response response = api.send(kick(partyId, leader, member).header("If-Match", "\"2\""));

        assertEquals(200, response.status(), "If-Match \"2\" matches the party at version 2");
        assertEquals(1, response.json().get("members").size(), "the member must be kicked");
        assertEquals("\"3\"", response.etag(), "the response carries the party's new version");
    }

    @Test
    void weakIfMatchNeverMatches() {
        UUID leader = api.login(PROXY, name("lead"));
        String memberName = name("mem");
        UUID member = api.login(PROXY, memberName);
        String partyId = joinedParty(leader, member, memberName);

        Response response = api.send(kick(partyId, leader, member).header("If-Match", "W/\"2\""));

        assertEquals(412, response.status(), "a weak tag must not satisfy If-Match");
        assertEquals("precondition-failed", response.problemCode(), "the problem code names the failed precondition");
    }

    private Response create(UUID leader) {
        return api.postJson("/v1/parties", "{\"leaderId\":\"" + leader + "\"}");
    }

    private static MutableHttpRequest<String> kick(String partyId, UUID actor, UUID target) {
        return HttpRequest.POST("/v1/parties/" + partyId + "/kick",
                "{\"actorId\":\"" + actor + "\",\"targetId\":\"" + target + "\"}")
                .contentType(MediaType.APPLICATION_JSON_TYPE);
    }

    /** Leader invites the member and the member accepts; returns the party id (version 2 afterwards). */
    private String joinedParty(UUID leader, UUID member, String memberName) {
        Response invited = api.postJson("/v1/parties/invites",
                "{\"actorId\":\"" + leader + "\",\"targetName\":\"" + memberName + "\"}");
        String partyId = invited.json().get("partyId").asText();
        api.send(HttpRequest.POST("/v1/players/" + member + "/invites/" + partyId + "/accept", ""));
        return partyId;
    }

    static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }
}
