package net.onelitefeather.quelea.backend.security;

import io.micronaut.security.token.Claims;
import net.onelitefeather.quelea.backend.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeClaimsValidatorTest {

    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final Duration SKEW = Duration.ofSeconds(60);

    private final MutableClock clock = new MutableClock(NOW);
    private final TimeClaimsValidator validator = new TimeClaimsValidator(SKEW, clock);

    private static Claims claims(Instant exp, Instant nbf) {
        Map<String, Object> map = new HashMap<>();
        if (exp != null) {
            map.put(Claims.EXPIRATION_TIME, Date.from(exp));
        }
        if (nbf != null) {
            map.put(Claims.NOT_BEFORE, Date.from(nbf));
        }
        return new Claims() {
            @Override
            public Object get(String name) {
                return map.get(name);
            }

            @Override
            public Set<String> names() {
                return map.keySet();
            }

            @Override
            public boolean contains(String name) {
                return map.containsKey(name);
            }
        };
    }

    @Test
    void aTokenExpiredWithinTheSkewIsAccepted() {
        assertTrue(validator.validate(claims(NOW.minusSeconds(30), null), null), "expired 30 s ago, skew 60 s");
    }

    @Test
    void aTokenExpiredBeyondTheSkewIsRefused() {
        assertFalse(validator.validate(claims(NOW.minusSeconds(61), null), null), "expired 61 s ago, skew 60 s");
    }

    @Test
    void aTokenWithoutExpirationIsRefused() {
        assertFalse(validator.validate(claims(null, null), null), "exp is required");
    }

    @Test
    void aTokenNotYetValidWithinTheSkewIsAccepted() {
        assertTrue(validator.validate(claims(NOW.plusSeconds(3600), NOW.plusSeconds(30)), null), "nbf 30 s ahead, skew 60 s");
    }

    @Test
    void aTokenNotYetValidBeyondTheSkewIsRefused() {
        assertFalse(validator.validate(claims(NOW.plusSeconds(3600), NOW.plusSeconds(61)), null), "nbf 61 s ahead, skew 60 s");
    }

    @Test
    void aSkewAboveTheMaximumStopsTheStart() {
        assertThrows(IllegalStateException.class, () -> new TimeClaimsValidator(Duration.ofSeconds(61), clock),
                "the skew cannot be widened past 60 s");
    }

    @Test
    void aNegativeSkewStopsTheStart() {
        assertThrows(IllegalStateException.class, () -> new TimeClaimsValidator(Duration.ofSeconds(-1), clock),
                "a negative skew is refused");
    }
}
