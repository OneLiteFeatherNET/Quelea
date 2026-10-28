package net.onelitefeather.quelea.backend.presence;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

@Serdeable
public record PlayerPresence(UUID id, String name, String proxyId, Instant since) {
}
