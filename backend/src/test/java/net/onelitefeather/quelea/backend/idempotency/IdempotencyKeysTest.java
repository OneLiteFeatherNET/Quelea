package net.onelitefeather.quelea.backend.idempotency;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdempotencyKeysTest {

    @Test
    void visibleAsciiKeyIsValid() {
        assertTrue(IdempotencyKeys.isValid("order-42_ab!"), "visible ASCII characters make a valid key");
    }

    @Test
    void emptyKeyIsInvalid() {
        assertFalse(IdempotencyKeys.isValid(""), "an empty key carries no identity");
    }

    @Test
    void keyOf255CharactersIsValid() {
        assertTrue(IdempotencyKeys.isValid("k".repeat(255)), "255 characters is the largest valid key");
    }

    @Test
    void keyOf256CharactersIsInvalid() {
        assertFalse(IdempotencyKeys.isValid("k".repeat(256)), "256 characters is too long");
    }

    @Test
    void keyWithSpaceIsInvalid() {
        assertFalse(IdempotencyKeys.isValid("with space"), "a space is not a visible character");
    }

    @Test
    void keyWithNonAsciiCharacterIsInvalid() {
        assertFalse(IdempotencyKeys.isValid("schlüssel"), "only visible ASCII is allowed");
    }

    @Test
    void fingerprintOfTheSameRequestIsEqual() {
        byte[] body = "{\"message\":\"hi\"}".getBytes(StandardCharsets.UTF_8);

        assertEquals(IdempotencyKeys.fingerprint("POST", "/v1/x", body), IdempotencyKeys.fingerprint("POST", "/v1/x", body),
                "the same method, path and body must give the same fingerprint");
    }

    @Test
    void fingerprintChangesWithTheBody() {
        assertNotEquals(
                IdempotencyKeys.fingerprint("POST", "/v1/x", "{\"message\":\"hi\"}".getBytes(StandardCharsets.UTF_8)),
                IdempotencyKeys.fingerprint("POST", "/v1/x", "{\"message\":\"bye\"}".getBytes(StandardCharsets.UTF_8)),
                "a different body is a different request");
    }
}
