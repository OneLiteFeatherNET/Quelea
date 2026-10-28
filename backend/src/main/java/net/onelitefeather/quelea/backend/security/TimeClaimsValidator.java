package net.onelitefeather.quelea.backend.security;

import io.micronaut.security.token.Claims;
import io.micronaut.security.token.jwt.validator.GenericJwtClaimsValidator;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Checks {@code exp} and {@code nbf} with an explicit, bounded clock skew (RFC 8725). A token must carry
 * {@code exp}; {@code nbf} is optional. The library's own expiration and not-before validators are switched
 * off in {@code application.yml} because they have no leeway. The skew can only be lowered: a value above
 * {@link #MAX_SKEW} or below zero stops the application from starting.
 */
@Singleton
public class TimeClaimsValidator implements GenericJwtClaimsValidator<Object> {

    /** The largest clock skew the application will ever accept. */
    public static final Duration MAX_SKEW = Duration.ofSeconds(60);

    private final Clock clock;
    private final Duration skew;

    public TimeClaimsValidator(QueleaAuthProperties properties, Clock clock) {
        this(properties.clockSkew(), clock);
    }

    TimeClaimsValidator(Duration skew, Clock clock) {
        if (skew.isNegative() || skew.compareTo(MAX_SKEW) > 0) {
            throw new IllegalStateException("quelea.auth.clock-skew (QUELEA_AUTH_CLOCK_SKEW) must be between 0s and "
                    + MAX_SKEW.toSeconds() + "s but is " + skew);
        }
        this.skew = skew;
        this.clock = clock;
    }

    @Override
    public boolean validate(Claims claims, Object request) {
        Instant now = this.clock.instant();
        Instant expiry = instant(claims.get(Claims.EXPIRATION_TIME));
        if (expiry == null || !now.minus(this.skew).isBefore(expiry)) {
            return false;
        }
        Object notBeforeClaim = claims.get(Claims.NOT_BEFORE);
        if (notBeforeClaim == null) {
            return true;
        }
        Instant notBefore = instant(notBeforeClaim);
        return notBefore != null && !now.plus(this.skew).isBefore(notBefore);
    }

    private static Instant instant(Object claim) {
        if (claim instanceof Date date) {
            return date.toInstant();
        }
        if (claim instanceof Number number) {
            return Instant.ofEpochSecond(number.longValue());
        }
        return null;
    }
}
