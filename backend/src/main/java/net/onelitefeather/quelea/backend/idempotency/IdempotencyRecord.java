package net.onelitefeather.quelea.backend.idempotency;

import io.micronaut.serde.annotation.Serdeable;

/**
 * What a client+key pair holds. While the request runs, {@code response} is {@code null} and the record is the
 * in-progress marker. Once it succeeded, {@code response} is the response to replay.
 *
 * @param fingerprint {@code method + path + sha256(body)} of the request that claimed the key
 * @param response    the stored response, or {@code null} while the request is in progress
 */
@Serdeable
public record IdempotencyRecord(String fingerprint, StoredResponse response) {

    public boolean completed() {
        return response != null;
    }
}
