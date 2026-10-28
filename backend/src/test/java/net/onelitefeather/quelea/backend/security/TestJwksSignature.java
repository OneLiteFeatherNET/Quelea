package net.onelitefeather.quelea.backend.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import io.micronaut.context.annotation.Requires;
import io.micronaut.security.token.jwt.signature.SignatureConfiguration;
import io.micronaut.security.token.jwt.signature.jwks.DefaultJwkValidator;
import io.micronaut.security.token.jwt.signature.jwks.JWKSetJwksSignature;
import jakarta.inject.Singleton;

/**
 * The {@code test} environment's signature source: the public key of {@link TestJwtIssuer}, so verifying a token
 * never touches the network. The production JWKS validator is not registered in this environment.
 */
@Singleton
@Requires(env = "test")
public class TestJwksSignature implements SignatureConfiguration {

    private final JWKSetJwksSignature delegate;

    public TestJwksSignature(TestJwtIssuer issuer) {
        this.delegate = new JWKSetJwksSignature(new DefaultJwkValidator(), issuer.publicKeys());
    }

    @Override
    public String supportedAlgorithmsMessage() {
        return this.delegate.supportedAlgorithmsMessage();
    }

    @Override
    public boolean supports(JWSAlgorithm algorithm) {
        return this.delegate.supports(algorithm);
    }

    @Override
    public boolean verify(SignedJWT jwt) throws JOSEException {
        return this.delegate.verify(jwt);
    }
}
