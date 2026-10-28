package net.onelitefeather.quelea.backend.api;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Serdeable
public record ActorRequest(@NotNull UUID actorId) {
}
