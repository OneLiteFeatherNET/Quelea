package net.onelitefeather.quelea.backend.security;

import com.nimbusds.jose.jwk.KeyType;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.security.token.jwt.signature.jwks.JwksSignatureConfiguration;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Registers the issuer's JWKS as the signature source. Micronaut Security turns every
 * {@link JwksSignatureConfiguration} bean into a signature validator that fetches and caches the
 * keys, pinned to RS256 by {@link Rs256PinnedJwksSignature}. It is not registered in the {@code test}
 * environment, where tests sign with their own key and no key is ever fetched over the network.
 */
@Factory
public class JwksSignatureFactory {

    @Singleton
    @Named("issuer")
    @Requires(notEnv = "test")
    JwksSignatureConfiguration issuerJwks(QueleaAuthProperties properties) {
        return new JwksSignatureConfiguration() {
            @Override
            public String getName() {
                return "issuer";
            }

            @Override
            public String getUrl() {
                return properties.jwksUrl();
            }

            @Override
            public KeyType getKeyType() {
                return KeyType.RSA;
            }

            @Override
            @SuppressWarnings("removal")
            public Integer getCacheExpiration() {
                return 60;
            }
        };
    }
}
