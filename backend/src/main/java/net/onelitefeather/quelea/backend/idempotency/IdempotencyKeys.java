package net.onelitefeather.quelea.backend.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Rules of the {@code Idempotency-Key} header: what a valid key is, how keys are stored and how requests are compared. */
public final class IdempotencyKeys {

    static final int MAX_LENGTH = 255;

    private IdempotencyKeys() {
    }

    /** One to 255 visible ASCII characters (0x21 to 0x7E). */
    public static boolean isValid(String key) {
        if (key.isEmpty() || key.length() > MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c < 0x21 || c > 0x7E) {
                return false;
            }
        }
        return true;
    }

    /** The SHA-256 of the key as hex: the key as it is stored, so the store never holds the raw key. */
    public static String hash(String key) {
        return sha256(key.getBytes(StandardCharsets.US_ASCII));
    }

    /** {@code method + " " + path + " " + sha256(body)}: two requests with the same fingerprint are the same request. */
    public static String fingerprint(String method, String path, byte[] body) {
        return method + " " + path + " " + sha256(body);
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
