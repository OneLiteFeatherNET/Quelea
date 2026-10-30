package net.onelitefeather.quelea.backend.api;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(rebuildContext = true)
class JoinRequestsControllerTest {

    static final String PROXY = "proxy-requests";

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
    void requestToPrivatePartyAnswers201WithTheOpenRequest() {
        String leaderName = name("lead");
        UUID leader = api.login(PROXY, leaderName);
        String partyId = createParty(leader);
        UUID requester = api.login(PROXY, name("req"));

        Response response = request(requester, leaderName);

        assertEquals(201, response.status());
        assertEquals(partyId, response.json().get("partyId").asText());
        assertEquals(requester.toString(), response.json().get("requesterId").asText());
        assertTrue(response.json().hasNonNull("expiresAt"), "the request must tell when it expires");
    }

    @Test
    void requestToPublicPartyAnswers200WithThePartyAndTheRequesterAsMember() {
        String leaderName = name("lead");
        UUID leader = api.login(PROXY, leaderName);
        String partyId = createParty(leader);
        setPublic(partyId, leader, true);
        UUID requester = api.login(PROXY, name("req"));

        Response response = request(requester, leaderName);

        assertEquals(200, response.status());
        assertEquals(partyId, response.json().get("id").asText());
        assertEquals(2, response.json().get("members").size(), "the requester must be a member of the public party");
    }

    @Test
    void joinOnPrivatePartyAnswers409PartyNotPublic() {
        String leaderName = name("lead");
        UUID leader = api.login(PROXY, leaderName);
        createParty(leader);
        UUID joiner = api.login(PROXY, name("join"));

        Response response = api.send(HttpRequest.POST("/v1/players/" + joiner + "/party/join",
                "{\"targetName\":\"" + leaderName + "\"}"));

        assertEquals(409, response.status());
        assertEquals("party-not-public", response.problemCode());
    }

    @Test
    void secondRequestWhileOpenAnswers409JoinRequestPending() {
        String leaderName = name("lead");
        UUID leader = api.login(PROXY, leaderName);
        createParty(leader);
        UUID requester = api.login(PROXY, name("req"));
        request(requester, leaderName);

        Response response = request(requester, leaderName);

        assertEquals(409, response.status());
        assertEquals("join-request-pending", response.problemCode());
    }

    @Test
    void requestAfterADenialAnswers429WithRetryAfter() {
        String leaderName = name("lead");
        UUID leader = api.login(PROXY, leaderName);
        String partyId = createParty(leader);
        String deniedName = name("who");
        UUID denied = api.login(PROXY, deniedName);
        request(denied, leaderName);
        Response rejection = api.postJson("/v1/parties/" + partyId + "/join-requests/reject",
                "{\"actorId\":\"" + leader + "\",\"requesterName\":\"" + deniedName + "\"}");
        assertEquals(204, rejection.status());

        Response retry = request(denied, leaderName);

        assertEquals(429, retry.status());
        assertEquals("join-request-cooldown", retry.problemCode());
        assertFalse(retry.retryAfter().isEmpty(), "a cooldown answer must carry Retry-After");
    }

    @Test
    void requestToAPlayerInNoPartyAnswers404TargetNotInParty() {
        String targetName = name("lonely");
        api.login(PROXY, targetName);
        UUID requester = api.login(PROXY, name("req"));

        Response response = request(requester, targetName);

        assertEquals(404, response.status());
        assertEquals("target-not-in-party", response.problemCode());
    }

    private Response request(UUID requester, String targetName) {
        return api.postJson("/v1/players/" + requester + "/party/requests", "{\"targetName\":\"" + targetName + "\"}");
    }

    private void setPublic(String partyId, UUID leader, boolean open) {
        Response response = api.postJson("/v1/parties/" + partyId + "/public",
                "{\"actorId\":\"" + leader + "\",\"public\":" + open + "}");
        assertEquals(200, response.status(), "the leader must be able to change the flag");
    }

    private String createParty(UUID leader) {
        return api.postJson("/v1/parties", "{\"leaderId\":\"" + leader + "\"}").json().get("id").asText();
    }

    static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }
}
