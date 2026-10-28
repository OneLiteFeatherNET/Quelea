package net.onelitefeather.quelea.velocity.auth;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientCredentialsTokenProviderTest {

    private static final String SECRET = "s3cr3t/value+with=chars";

    /** Answers the token requests from a script and optionally holds the first one until released. */
    private static final class FakeTransport implements TokenTransport {
        private final List<TokenTransport.Response> script = new ArrayList<>();
        private final List<URI> uris = new ArrayList<>();
        private final List<String> forms = new ArrayList<>();
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release;
        private IOException failure;

        FakeTransport(CountDownLatch release) {
            this.release = release;
        }

        FakeTransport() {
            this(null);
        }

        FakeTransport answer(int status, String body) {
            this.script.add(new TokenTransport.Response(status, body));
            return this;
        }

        FakeTransport ok(String token, long expiresIn) {
            return answer(200, "{\"token_type\":\"Bearer\",\"expires_in\":" + expiresIn + ",\"access_token\":\"" + token + "\"}");
        }

        FakeTransport failWith(IOException failure) {
            this.failure = failure;
            return this;
        }

        @Override
        public TokenTransport.Response post(URI uri, String form) throws IOException {
            int call;
            synchronized (this) {
                this.uris.add(uri);
                this.forms.add(form);
                call = this.uris.size();
            }
            this.entered.countDown();
            if (this.release != null) {
                try {
                    this.release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (this.failure != null) {
                throw this.failure;
            }
            return this.script.get(Math.min(call, this.script.size()) - 1);
        }

        synchronized int calls() {
            return this.uris.size();
        }
    }

    private static ClientCredentials credentials() {
        return new ClientCredentials("tenant-1", "client-1", SECRET, "api://api-1/.default");
    }

    @Test
    void theRequestIsAClientCredentialsGrantToTheTenantsEndpoint() throws Exception {
        FakeTransport transport = new FakeTransport().ok("tok", 3600);

        new ClientCredentialsTokenProvider(credentials(), transport, new MutableClock()).token();

        assertEquals(URI.create("https://login.microsoftonline.com/tenant-1/oauth2/v2.0/token"), transport.uris.getFirst(),
                "default endpoint of the tenant");
        assertEquals("grant_type=client_credentials&client_id=client-1&client_secret="
                        + "s3cr3t%2Fvalue%2Bwith%3Dchars&scope=api%3A%2F%2Fapi-1%2F.default",
                transport.forms.getFirst(), "form body, url-encoded");
    }

    @Test
    void aTokenIsReusedInsideItsLifetime() throws Exception {
        FakeTransport transport = new FakeTransport().ok("tok", 3600);
        ClientCredentialsTokenProvider provider = new ClientCredentialsTokenProvider(credentials(), transport, new MutableClock());

        String first = provider.token();
        String second = provider.token();

        assertEquals(first, second, "same token");
        assertEquals(1, transport.calls(), "one token request for two calls");
    }

    @Test
    void aTokenIsStillReusedJustBeforeTheSkewStarts() throws Exception {
        MutableClock clock = new MutableClock();
        FakeTransport transport = new FakeTransport().ok("tok", 3600);
        ClientCredentialsTokenProvider provider = new ClientCredentialsTokenProvider(credentials(), transport, clock);
        provider.token();

        clock.advance(Duration.ofSeconds(3600 - 61));
        provider.token();

        assertEquals(1, transport.calls(), "61 seconds before expiry the token is still used");
    }

    @Test
    void aTokenIsRefreshedSixtySecondsBeforeItExpires() throws Exception {
        MutableClock clock = new MutableClock();
        FakeTransport transport = new FakeTransport().ok("old", 3600).ok("new", 3600);
        ClientCredentialsTokenProvider provider = new ClientCredentialsTokenProvider(credentials(), transport, clock);
        provider.token();

        clock.advance(Duration.ofSeconds(3600).minus(ClientCredentialsTokenProvider.SKEW));

        assertEquals("new", provider.token(), "within 60 seconds of expiry a new token is requested first");
        assertEquals(2, transport.calls(), "two token requests");
    }

    @Test
    void concurrentCallersCauseOneTokenRequest() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        FakeTransport transport = new FakeTransport(release).ok("tok", 3600);
        ClientCredentialsTokenProvider provider = new ClientCredentialsTokenProvider(credentials(), transport, new MutableClock());
        int callers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                results.add(pool.submit(provider::token));
            }
            assertTrue(transport.entered.await(10, TimeUnit.SECONDS), "the first request is in flight");
            release.countDown();

            for (Future<String> result : results) {
                assertEquals("tok", result.get(10, TimeUnit.SECONDS), "every caller gets the token");
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, transport.calls(), "eight callers, one token request");
    }

    @Test
    void aRejectedTokenIsReplacedOnTheNextCall() throws Exception {
        FakeTransport transport = new FakeTransport().ok("old", 3600).ok("new", 3600);
        ClientCredentialsTokenProvider provider = new ClientCredentialsTokenProvider(credentials(), transport, new MutableClock());
        String old = provider.token();

        provider.rejected(old);

        assertEquals("new", provider.token(), "a rejected token is not handed out again");
    }

    @Test
    void aLateRejectionOfAnOlderTokenKeepsTheCurrentOne() throws Exception {
        FakeTransport transport = new FakeTransport().ok("current", 3600);
        ClientCredentialsTokenProvider provider = new ClientCredentialsTokenProvider(credentials(), transport, new MutableClock());
        provider.token();

        provider.rejected("an-older-token");

        assertEquals("current", provider.token(), "the current token survives");
        assertEquals(1, transport.calls(), "no extra token request");
    }

    @Test
    void aRefusedRequestSurfacesStatusAndReasonWithoutTheSecret() {
        FakeTransport transport = new FakeTransport().answer(401,
                "{\"error\":\"invalid_client\",\"error_description\":\"AADSTS7000215: Invalid client secret provided.\"}");
        ClientCredentialsTokenProvider provider = new ClientCredentialsTokenProvider(credentials(), transport, new MutableClock());

        TokenException e = assertThrows(TokenException.class, provider::token);

        assertTrue(e.getMessage().contains("HTTP 401") && e.getMessage().contains("invalid_client")
                        && e.getMessage().contains("AADSTS7000215"),
                "status and reason are named: " + e.getMessage());
        assertFalse(e.getMessage().contains(SECRET), "the secret never appears in the message");
    }

    @Test
    void anUnreachableEndpointSurfacesAsATokenExceptionWithItsCause() {
        IOException cause = new IOException("connection refused");
        FakeTransport transport = new FakeTransport().failWith(cause);
        ClientCredentialsTokenProvider provider = new ClientCredentialsTokenProvider(credentials(), transport, new MutableClock());

        TokenException e = assertThrows(TokenException.class, provider::token);

        assertEquals(cause, e.getCause(), "the transport failure is kept as the cause");
        assertTrue(e.getMessage().contains("connection refused"), "the reason is named: " + e.getMessage());
        assertFalse(e.getMessage().contains(SECRET), "the secret never appears in the message");
    }

    @Test
    void aResponseWithoutATokenIsRejected() {
        FakeTransport transport = new FakeTransport().answer(200, "{\"expires_in\":3600}");
        ClientCredentialsTokenProvider provider = new ClientCredentialsTokenProvider(credentials(), transport, new MutableClock());

        TokenException e = assertThrows(TokenException.class, provider::token);

        assertTrue(e.getMessage().contains("access_token"), "names what is missing: " + e.getMessage());
    }

    @Test
    void aResponseThatIsNotJsonIsRejected() {
        FakeTransport transport = new FakeTransport().answer(200, "<html>proxy login</html>");
        ClientCredentialsTokenProvider provider = new ClientCredentialsTokenProvider(credentials(), transport, new MutableClock());

        assertThrows(TokenException.class, provider::token);
    }

    @Test
    void aFailedRequestIsNotRememberedAndTheNextCallTriesAgain() throws Exception {
        FakeTransport transport = new FakeTransport().answer(503, "").ok("tok", 3600);
        ClientCredentialsTokenProvider provider = new ClientCredentialsTokenProvider(credentials(), transport, new MutableClock());
        assertThrows(TokenException.class, provider::token);

        assertEquals("tok", provider.token(), "the second call succeeds");
        assertEquals(2, transport.calls(), "the second call sent a new request");
    }

    @Test
    void incompleteCredentialsAreRefusedAtConstruction() {
        ClientCredentials incomplete = new ClientCredentials("tenant-1", "client-1", "", "api://api-1/.default");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new ClientCredentialsTokenProvider(incomplete, new FakeTransport(), new MutableClock()));

        assertTrue(e.getMessage().contains("client-secret"), "names what is missing: " + e.getMessage());
    }
}
