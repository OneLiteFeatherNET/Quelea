package net.onelitefeather.quelea.backend.api;

import io.micrometer.core.instrument.MeterRegistry;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest(rebuildContext = true)
class ObservabilityEndpointsTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    MeterRegistry registry;

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
    void prometheusScrapeEndpointIsGone() {
        Response response = api.send(HttpRequest.GET("/prometheus"));

        assertEquals(404, response.status(), "metrics are pushed over OTLP, so there is no scrape endpoint");
        assertEquals("not-found", response.problemCode());
    }

    @Test
    void serviceGaugesAreRegistered() {
        assertNotNull(registry.find("quelea.sse.streams").gauge(), "the open-streams gauge must be registered");
        assertNotNull(registry.find("quelea.parties.active").gauge(), "the active-parties gauge must be registered");
    }

    @Test
    void operationCounterIncrementsAfterAFailedInvite() {
        double before = operationCount("invite", "player-not-online");

        api.postJson("/v1/parties/invites", "{\"actorId\":\"" + java.util.UUID.randomUUID() + "\",\"targetName\":\"nobody\"}");

        assertEquals(before + 1.0, operationCount("invite", "player-not-online"),
                "a failed invite must be counted under its problem code");
    }

    private double operationCount(String operation, String result) {
        var counter = registry.find("quelea.party.operations").tags("operation", operation, "result", result).counter();
        return counter == null ? 0.0 : counter.count();
    }
}
