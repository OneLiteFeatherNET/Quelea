package net.onelitefeather.quelea.backend.api;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import net.onelitefeather.quelea.backend.event.EventBroadcaster;
import net.onelitefeather.quelea.backend.event.PartyEvent;
import net.onelitefeather.quelea.backend.idempotency.IdempotencyKeys;
import net.onelitefeather.quelea.backend.idempotency.IdempotencyRecord;
import net.onelitefeather.quelea.backend.idempotency.IdempotencyStore;
import net.onelitefeather.quelea.backend.security.Roles;
import net.onelitefeather.quelea.backend.security.TestJwtIssuer;
import net.onelitefeather.quelea.backend.support.ApiClient;
import net.onelitefeather.quelea.backend.support.ApiClient.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Idempotency-Key on POST /v1/**: replay, conflicts, failures, client scope and key validation, over HTTP. */
@MicronautTest
class IdempotencyTest {

    static final String PROXY = "proxy-idempotency";
    /** A second registered client, to prove that keys are scoped per client. */
    static final String OTHER_CLIENT = "00000000-0000-4000-8000-0000000000c2";

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    TestJwtIssuer jwt;

    @Inject
    EventBroadcaster broadcaster;

    @Inject
    IdempotencyStore store;

    private ApiClient api;
    private final List<PartyEvent> events = new CopyOnWriteArrayList<>();
    private Disposable subscription;

    @BeforeEach
    void setUp() {
        api = new ApiClient(client, jwt);
        subscription = broadcaster.stream().subscribe(events::add);
    }

    @AfterEach
    void tearDown() {
        subscription.dispose();
    }

    @Test
    void replayedChatIsPublishedOnce() {
        UUID leader = api.login(PROXY, name("lead"));
        String partyId = createParty(leader);
        String key = UUID.randomUUID().toString();

        Response first = api.send(chat(partyId, leader, "hello").header("Idempotency-Key", key));
        Response second = api.send(chat(partyId, leader, "hello").header("Idempotency-Key", key));

        assertEquals(202, first.status());
        assertEquals(202, second.status(), "the replay answers with the stored response");
        assertEquals(1, chatsWithMessage(partyId, "hello").size(), "the chat must be published once, not once per request");
    }

    @Test
    void replayedCreateAnswersTheSamePartyWithoutCreatingAnother() {
        UUID leader = api.login(PROXY, name("lead"));
        String key = UUID.randomUUID().toString();

        Response first = api.send(createRequest(leader).header("Idempotency-Key", key));
        Response second = api.send(createRequest(leader).header("Idempotency-Key", key));

        assertEquals(201, second.status(), "a second create for a leader already in a party would answer 409 if it ran");
        assertEquals(first.json().get("id").asText(), second.json().get("id").asText(), "the replay returns the party created first");
        assertEquals("\"1\"", second.etag(), "the replay keeps the entity tag of the stored response");
    }

    @Test
    void sameKeyWithAnotherBodyAnswers422() {
        UUID leader = api.login(PROXY, name("lead"));
        String partyId = createParty(leader);
        String key = UUID.randomUUID().toString();
        api.send(chat(partyId, leader, "hi").header("Idempotency-Key", key));

        Response response = api.send(chat(partyId, leader, "bye").header("Idempotency-Key", key));

        assertEquals(422, response.status(), "the same key with a different body is a misuse");
        assertEquals("idempotency-key-reused", response.problemCode());
    }

    @Test
    void duplicateWhileTheFirstRequestRunsAnswers409WithRetryAfter() {
        UUID leader = api.login(PROXY, name("lead"));
        String partyId = createParty(leader);
        String key = UUID.randomUUID().toString();
        String path = "/v1/parties/" + partyId + "/chat";
        String body = chatBody(leader, "again");
        // The first request is still running: its in-progress marker is in the store.
        String fingerprint = IdempotencyKeys.fingerprint("POST", path, body.getBytes(StandardCharsets.UTF_8));
        store.claim(TestJwtIssuer.CLIENT, key, new IdempotencyRecord(fingerprint, null), Duration.ofSeconds(10));

        HttpClientResponseException rejected = assertRejected(
                HttpRequest.POST(path, body).contentType(MediaType.APPLICATION_JSON_TYPE).header("Idempotency-Key", key));

        assertEquals(409, rejected.getStatus().getCode(), "a duplicate of a running request answers 409");
        assertEquals("1", rejected.getResponse().getHeaders().get("Retry-After"), "the client is told to retry after one second");
        assertEquals(0, chatsWithMessage(partyId, "again").size(), "the duplicate must not publish anything");
    }

    @Test
    void failedRequestIsNotStoredAndTheRetryRuns() {
        UUID leader = api.login(PROXY, name("lead"));
        String targetName = name("tgt");
        String key = UUID.randomUUID().toString();

        Response first = api.send(invite(leader, targetName).header("Idempotency-Key", key));
        api.login(PROXY, targetName);
        Response retry = api.send(invite(leader, targetName).header("Idempotency-Key", key));

        assertEquals(404, first.status(), "the first attempt fails: the target is offline");
        assertEquals(201, retry.status(), "a failed request must not be replayed, so the retry runs and succeeds");
    }

    @Test
    void keysAreScopedPerClient() {
        UUID leader = api.login(PROXY, name("lead"));
        String partyId = createParty(leader);
        String key = UUID.randomUUID().toString();
        String otherToken = jwt.appOnly().claim("azp", OTHER_CLIENT).roles(Roles.CLIENT).sign();

        Response first = api.send(chat(partyId, leader, "from-first-client").header("Idempotency-Key", key));
        Response second = api.send(chat(partyId, leader, "from-second-client")
                .header("Idempotency-Key", key).bearerAuth(otherToken));

        assertEquals(202, first.status(), "the first client's request runs");
        assertEquals(202, second.status(), "the same key from another client is a different key and runs too");
        assertEquals(1, chatsWithMessage(partyId, "from-second-client").size(), "the second client's chat must be published");
    }

    @Test
    void keyLongerThan255CharactersAnswers400() {
        UUID leader = api.login(PROXY, name("lead"));
        String partyId = createParty(leader);

        Response response = api.send(chat(partyId, leader, "hi").header("Idempotency-Key", "k".repeat(256)));

        assertEquals(400, response.status(), "an over-long key is a validation error");
        assertEquals("validation-failed", response.problemCode());
    }

    @Test
    void anonymousRetryIsRefusedInsteadOfReplayed() {
        UUID leader = api.login(PROXY, name("lead"));
        String partyId = createParty(leader);
        String key = UUID.randomUUID().toString();
        api.send(chat(partyId, leader, "hi").header("Idempotency-Key", key));

        Response anonymous = api.sendAnonymously(chat(partyId, leader, "hi").header("Idempotency-Key", key));

        assertEquals(401, anonymous.status(), "the security filter runs first, so a stored response is never replayed to an anonymous caller");
    }

    private String createParty(UUID leader) {
        return api.send(createRequest(leader)).json().get("id").asText();
    }

    private static MutableHttpRequest<String> createRequest(UUID leader) {
        return HttpRequest.POST("/v1/parties", "{\"leaderId\":\"" + leader + "\"}")
                .contentType(MediaType.APPLICATION_JSON_TYPE);
    }

    private static MutableHttpRequest<String> chat(String partyId, UUID actor, String message) {
        return HttpRequest.POST("/v1/parties/" + partyId + "/chat", chatBody(actor, message))
                .contentType(MediaType.APPLICATION_JSON_TYPE);
    }

    private static String chatBody(UUID actor, String message) {
        return "{\"actorId\":\"" + actor + "\",\"message\":\"" + message + "\"}";
    }

    private static MutableHttpRequest<String> invite(UUID actor, String targetName) {
        return HttpRequest.POST("/v1/parties/invites",
                        "{\"actorId\":\"" + actor + "\",\"targetName\":\"" + targetName + "\"}")
                .contentType(MediaType.APPLICATION_JSON_TYPE);
    }

    /** Sends the request with a valid token and returns the rejection, which the client raises for a 4xx answer. */
    private HttpClientResponseException assertRejected(MutableHttpRequest<?> request) {
        try {
            client.toBlocking().exchange(request.bearerAuth(jwt.clientToken()), String.class);
        } catch (HttpClientResponseException e) {
            return e;
        }
        throw new AssertionError("expected the request to be rejected");
    }

    private List<PartyEvent> chatsWithMessage(String partyId, String message) {
        return events.stream()
                .filter(event -> event.type().equals("party.chat"))
                .filter(event -> event.partyId().toString().equals(partyId))
                .filter(event -> message.equals(event.payload().get("message")))
                .toList();
    }

    static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }
}
