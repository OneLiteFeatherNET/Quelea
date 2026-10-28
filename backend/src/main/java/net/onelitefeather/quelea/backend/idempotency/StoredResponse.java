package net.onelitefeather.quelea.backend.idempotency;

import io.micronaut.serde.annotation.Serdeable;

/**
 * A successful response kept for replay: status, content type, entity tag (or {@code null}) and the JSON body, which
 * is empty for responses without a body.
 */
@Serdeable
public record StoredResponse(int status, String contentType, String etag, String body) {
}
