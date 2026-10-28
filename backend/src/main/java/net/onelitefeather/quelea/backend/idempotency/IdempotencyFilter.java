package net.onelitefeather.quelea.backend.idempotency;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.serde.ObjectMapper;
import net.onelitefeather.quelea.backend.problem.PartyProblem;
import net.onelitefeather.quelea.backend.problem.ProblemFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Makes {@code POST /v1/**} safe to retry when the client sends an {@code Idempotency-Key}. Without the header the
 * request runs as before.
 * <ul>
 *   <li>A key that is not 1 to 255 visible ASCII characters answers 400 {@code validation-failed}.</li>
 *   <li>A key that is free is claimed with an in-progress marker for {@code quelea.idempotency.lock}. The response
 *       filter stores a 2xx response for {@code quelea.idempotency.ttl}; any other outcome releases the marker, so a
 *       retry runs again.</li>
 *   <li>A completed record with the same fingerprint replays its response without running the request.</li>
 *   <li>A record with another fingerprint answers 422 {@code idempotency-key-reused}; a marker with the same
 *       fingerprint answers 409 {@code request-in-progress} with {@code Retry-After: 1}.</li>
 * </ul>
 * Records are per authenticated client ({@code azp}). The filter is ordered after the security filter, so the principal
 * is known. The store blocks, so the filter methods run on the blocking executor.
 */
@ServerFilter(ServerFilter.MATCH_ALL_PATTERN)
public class IdempotencyFilter implements Ordered {

    static final String PATTERN = "/v1/**";
    static final String HEADER = "Idempotency-Key";
    static final String ANONYMOUS = "anonymous";
    static final String AUTHORIZED_PARTY_CLAIM = "azp";
    private static final String CLAIM_ATTRIBUTE = IdempotencyFilter.class.getName() + ".claim";

    /** The key this request claimed; the response filter settles it. */
    private record Claim(String client, String key, String fingerprint) {
    }

    private final IdempotencyStore store;
    private final ProblemFactory problems;
    private final ObjectMapper json;
    private final Duration recordTtl;
    private final Duration lockTtl;

    public IdempotencyFilter(IdempotencyStore store,
                             ProblemFactory problems,
                             ObjectMapper json,
                             @Value("${quelea.idempotency.ttl:24h}") Duration recordTtl,
                             @Value("${quelea.idempotency.lock:10s}") Duration lockTtl) {
        this.store = store;
        this.problems = problems;
        this.json = json;
        this.recordTtl = recordTtl;
        this.lockTtl = lockTtl;
    }

    /** After the security filter (which authenticates the request) and before the routes. */
    @Override
    public int getOrder() {
        return ServerFilterPhase.SECURITY.order() + 1;
    }

    /**
     * Returns the answer when the request must not run (invalid key, replay, conflict); returns {@code null} when it
     * runs, after claiming its key.
     */
    @RequestFilter(PATTERN)
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Nullable
    public MutableHttpResponse<?> claim(HttpRequest<?> request, @Body @Nullable byte[] body) {
        String key = request.getHeaders().get(HEADER);
        if (key == null || request.getMethod() != HttpMethod.POST) {
            return null;
        }
        if (!IdempotencyKeys.isValid(key)) {
            return problems.response(problems.create(PartyProblem.VALIDATION_FAILED,
                    "The Idempotency-Key header must be 1 to 255 visible ASCII characters.", request));
        }
        String client = clientOf(request);
        String fingerprint = IdempotencyKeys.fingerprint(request.getMethodName(), request.getPath(), body == null ? new byte[0] : body);
        var existing = store.claim(client, key, new IdempotencyRecord(fingerprint, null), lockTtl);
        if (existing.isPresent()) {
            return answer(existing.get(), fingerprint, request);
        }
        request.setAttribute(CLAIM_ATTRIBUTE, new Claim(client, key, fingerprint));
        return null;
    }

    /** Stores a 2xx response of a claimed request; any other outcome releases the claim. */
    @ResponseFilter(PATTERN)
    @ExecuteOn(TaskExecutors.BLOCKING)
    public void settle(HttpRequest<?> request, MutableHttpResponse<?> response) {
        Claim claim = request.getAttribute(CLAIM_ATTRIBUTE, Claim.class).orElse(null);
        if (claim == null) {
            return;
        }
        if (!isSuccessful(response)) {
            store.release(claim.client(), claim.key());
            return;
        }
        String body = serialise(response.getBody().orElse(null));
        String contentType = response.getContentType().map(MediaType::toString).orElse(MediaType.APPLICATION_JSON);
        String etag = response.getHeaders().get(HttpHeaders.ETAG);
        store.complete(claim.client(), claim.key(), new IdempotencyRecord(claim.fingerprint(),
                new StoredResponse(response.getStatus().getCode(), contentType, etag, body)), recordTtl);
        // The body is replaced by its stored bytes, so the first answer equals every replay.
        if (!body.isEmpty()) {
            response.body(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    private MutableHttpResponse<?> answer(IdempotencyRecord existing, String fingerprint, HttpRequest<?> request) {
        if (!existing.fingerprint().equals(fingerprint)) {
            return problems.response(problems.create(PartyProblem.IDEMPOTENCY_KEY_REUSED,
                    "The Idempotency-Key was used for another request.", request));
        }
        if (existing.completed()) {
            return replay(existing.response());
        }
        return problems.response(problems.create(PartyProblem.REQUEST_IN_PROGRESS,
                "The first request with this Idempotency-Key is still running.", request))
                .header(HttpHeaders.RETRY_AFTER, "1");
    }

    private MutableHttpResponse<?> replay(StoredResponse stored) {
        MutableHttpResponse<Object> response = HttpResponse.status(HttpStatus.valueOf(stored.status()));
        if (stored.etag() != null) {
            response.header(HttpHeaders.ETAG, stored.etag());
        }
        if (stored.body().isEmpty()) {
            return response;
        }
        return response.body(stored.body().getBytes(StandardCharsets.UTF_8))
                .contentType(MediaType.of(stored.contentType()));
    }

    private String serialise(Object body) {
        if (body == null) {
            return "";
        }
        try {
            return json.writeValueAsString(body);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot serialise the response body for replay", e);
        }
    }

    private static boolean isSuccessful(HttpResponse<?> response) {
        int code = response.getStatus().getCode();
        return code >= 200 && code <= 299;
    }

    /** The authenticated client's {@code azp}; without a principal (security disabled) all requests share one scope. */
    static String clientOf(HttpRequest<?> request) {
        return request.getUserPrincipal(Authentication.class)
                .map(Authentication::getAttributes)
                .map(attributes -> attributes.get(AUTHORIZED_PARTY_CLAIM))
                .map(String::valueOf)
                .orElse(ANONYMOUS);
    }
}
