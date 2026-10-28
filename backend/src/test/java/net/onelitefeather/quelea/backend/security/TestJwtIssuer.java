package net.onelitefeather.quelea.backend.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Signs access tokens for tests with its own RSA key, so no test needs Entra or any network. One instance per
 * application context: the context's {@link TestJwksSignature} trusts this key and nothing else. Token times come
 * from the injected {@link Clock}.
 */
@Singleton
@Requires(env = "test")
public class TestJwtIssuer {

    /** The issuer, audience and tenant the test environment accepts (see {@code application-test.yml}). */
    public static final String ISSUER = "http://localhost:18090/quelea";
    public static final String AUDIENCE = "quelea-api";
    public static final String TENANT = "00000000-0000-4000-8000-0000000000aa";

    /** The client id of the test client; it is the {@code azp} of every app-only token it issues. */
    public static final String CLIENT = "00000000-0000-4000-8000-0000000000c1";

    private static final Duration LIFETIME = Duration.ofHours(1);

    private final Clock clock;
    private final RSAKey key;

    public TestJwtIssuer(Clock clock) {
        this.clock = clock;
        this.key = newKey();
    }

    private static RSAKey newKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("test-key").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("cannot generate a test key", e);
        }
    }

    /** The public key of this issuer, as the application's signature source reads it. */
    public JWKSet publicKeys() {
        return new JWKSet(this.key.toPublicJWK());
    }

    /** A valid app-only token with the role {@link Roles#CLIENT}: what a Velocity proxy or the test client sends. */
    public String clientToken() {
        return appOnly().roles(Roles.CLIENT).sign();
    }

    /** A token under construction, starting from a valid app-only token. */
    public Token appOnly() {
        Instant now = this.clock.instant();
        return new Token(this.key)
                .claim("iss", ISSUER)
                .claim("ver", "2.0")
                .claim("aud", AUDIENCE)
                .claim("tid", TENANT)
                .claim("sub", UUID.randomUUID().toString())
                .claim("oid", UUID.randomUUID().toString())
                .claim("azp", CLIENT)
                .claim("iat", now.getEpochSecond())
                .claim("nbf", now.getEpochSecond())
                .claim("exp", now.plus(LIFETIME).getEpochSecond());
    }

    /** Builder for one token. Claims are kept in insertion order; times are epoch seconds. */
    public static final class Token {

        private final Map<String, Object> claims = new LinkedHashMap<>();
        private RSAKey signingKey;

        private Token(RSAKey key) {
            this.signingKey = key;
        }

        public Token claim(String name, Object value) {
            this.claims.put(name, value);
            return this;
        }

        public Token without(String name) {
            this.claims.remove(name);
            return this;
        }

        public Token roles(String... roles) {
            return claim("roles", List.of(roles));
        }

        public Token expiredAt(Instant when) {
            return claim("exp", when.getEpochSecond());
        }

        /** Signs with a different key than the one the application trusts. */
        public Token signedByAnotherKey() {
            this.signingKey = newKey();
            return this;
        }

        public String sign() {
            try {
                SignedJWT jwt = new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(this.signingKey.getKeyID()).build(), claimsSet());
                jwt.sign(new RSASSASigner(this.signingKey));
                return jwt.serialize();
            } catch (JOSEException e) {
                throw new IllegalStateException("cannot sign the test token", e);
            }
        }

        /** A key-confusion token: {@code HS256}, with the application's public key bytes as the HMAC secret. */
        public String hs256WithPublicKeyAsSecret() {
            try {
                SignedJWT jwt = new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(this.signingKey.getKeyID()).build(), claimsSet());
                jwt.sign(new MACSigner(this.signingKey.toRSAPublicKey().getEncoded()));
                return jwt.serialize();
            } catch (JOSEException e) {
                throw new IllegalStateException("cannot sign the key-confusion token", e);
            }
        }

        /** A token with {@code alg: none}, which must always be refused. */
        public String unsigned() {
            return new PlainJWT(claimsSet()).serialize();
        }

        private JWTClaimsSet claimsSet() {
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
            this.claims.forEach(builder::claim);
            return builder.build();
        }
    }
}
