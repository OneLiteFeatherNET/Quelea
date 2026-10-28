package net.onelitefeather.quelea.backend.api;

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

/** Presence is observed through the invite endpoint: a player is invitable exactly while they are online. */
@MicronautTest(rebuildContext = true)
class ProxiesControllerTest {

    static final String PROXY = "proxy-presence";

    @Inject
    @Client("/")
    HttpClient client;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(client);
    }

    @Test
    void loginAnswers204AndMakesThePlayerInvitable() {
        UUID leader = api.login(PROXY, name("lead"));
        String targetName = name("tgt");
        UUID target = UUID.randomUUID();

        Response response = api.putJson("/v1/proxies/" + PROXY + "/players/" + target, "{\"name\":\"" + targetName + "\"}");

        assertEquals(204, response.status());
        assertEquals(201, invite(leader, targetName).status(), "a logged-in player can be invited");
    }

    @Test
    void disconnectAnswers204AndMakesThePlayerUnreachable() {
        UUID leader = api.login(PROXY, name("lead"));
        String targetName = name("tgt");
        UUID target = api.login(PROXY, targetName);

        Response response = api.send(HttpRequest.DELETE("/v1/proxies/" + PROXY + "/players/" + target));

        assertEquals(204, response.status());
        Response invite = invite(leader, targetName);
        assertEquals(404, invite.status());
        assertEquals("player-not-online", invite.problemCode());
    }

    @Test
    void snapshotAnswers204AndReplacesThePlayerSet() {
        String leaderName = name("lead");
        UUID leader = api.login(PROXY, leaderName);
        String kept = name("kept");
        String dropped = name("drop");
        UUID keptId = UUID.randomUUID();
        api.login(PROXY, dropped);

        // A snapshot is the full set of the proxy's players, so the leader is listed too.
        Response response = api.putJson("/v1/proxies/" + PROXY + "/players",
                "[{\"id\":\"" + leader + "\",\"name\":\"" + leaderName + "\"},"
                        + "{\"id\":\"" + keptId + "\",\"name\":\"" + kept + "\"}]");

        assertEquals(204, response.status());
        assertEquals(201, invite(leader, kept).status(), "a player in the snapshot is online");
        assertEquals(404, invite(leader, dropped).status(), "a player left out of the snapshot is disconnected");
    }

    @Test
    void snapshotWithInvalidPlayerAnswers400ValidationFailed() {
        Response response = api.putJson("/v1/proxies/" + PROXY + "/players",
                "[{\"id\":\"" + UUID.randomUUID() + "\",\"name\":\"\"}]");

        assertEquals(400, response.status(), "an element of the snapshot is validated, not only the list");
        assertEquals("validation-failed", response.problemCode());
    }

    @Test
    void heartbeatAnswers204() {
        Response response = api.send(HttpRequest.PUT("/v1/proxies/" + PROXY + "/heartbeat", ""));

        assertEquals(204, response.status());
    }

    private Response invite(UUID actor, String targetName) {
        return api.postJson("/v1/parties/invites", "{\"actorId\":\"" + actor + "\",\"targetName\":\"" + targetName + "\"}");
    }

    static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }
}
