package net.onelitefeather.quelea.backend.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.SignedJWT;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.security.token.jwt.nimbus.ReactiveJwksSignature;
import io.micronaut.security.token.jwt.signature.jwks.JwkSetFetcher;
import io.micronaut.security.token.jwt.signature.jwks.JwkValidator;
import io.micronaut.security.token.jwt.signature.jwks.JwksSignatureConfiguration;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/**
 * The issuer's JWKS signature check. Where the library accepts every algorithm that fits the key type
 * (RS256, RS384, PS256 and so on), this check accepts exactly one: the token header must say
 * {@code RS256}. Every other {@code alg}, including the HMAC family used in key-confusion attacks, is
 * refused before any key is looked at.
 */
@EachBean(JwksSignatureConfiguration.class)
@Replaces(ReactiveJwksSignature.class)
public class Rs256PinnedJwksSignature extends ReactiveJwksSignature {

    public Rs256PinnedJwksSignature(
            JwksSignatureConfiguration configuration, JwkValidator validator, JwkSetFetcher<JWKSet> fetcher) {
        super(configuration, validator, fetcher);
    }

    @Override
    public Publisher<Boolean> verify(SignedJWT jwt) {
        if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
            return Mono.just(false);
        }
        return super.verify(jwt);
    }
}
