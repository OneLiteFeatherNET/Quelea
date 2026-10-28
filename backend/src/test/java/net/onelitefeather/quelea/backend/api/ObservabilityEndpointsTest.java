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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(rebuildContext = true)
class ObservabilityEndpointsTest {

    @Inject
    @Client("/")
    HttpClient client;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(client);
    }

    @Test
    void healthAnswers200WhenNoDependencyIsDown() {
        Response response = api.send(HttpRequest.GET("/health"));

        assertEquals(200, response.status(), "with Redis disabled there is no failing dependency");
    }

    @Test
    void prometheusExposesTheServiceGauges() {
        Response response = api.send(HttpRequest.GET("/prometheus"));

        assertEquals(200, response.status());
        assertTrue(response.body().contains("quelea_sse_streams"), "the open-streams gauge must be exposed");
        assertTrue(response.body().contains("quelea_parties_active"), "the active-parties gauge must be exposed");
    }

    @Test
    void operationCounterAppearsAfterAnOperation() {
        api.postJson("/v1/parties/invites", "{\"actorId\":\"" + java.util.UUID.randomUUID() + "\",\"targetName\":\"nobody\"}");

        Response response = api.send(HttpRequest.GET("/prometheus"));

        assertTrue(response.body().contains("quelea_party_operations_total{operation=\"invite\",result=\"player-not-online\""),
                "a failed invite must be counted under its problem code");
    }
}
