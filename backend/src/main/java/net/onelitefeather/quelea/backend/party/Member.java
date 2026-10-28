package net.onelitefeather.quelea.backend.party;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

@Serdeable
public record Member(UUID id, String name, Instant joinedAt) {
}
