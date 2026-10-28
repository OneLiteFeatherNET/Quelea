package net.onelitefeather.quelea.backend.presence;

import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record PlayerRef(UUID id, String name) {
}
