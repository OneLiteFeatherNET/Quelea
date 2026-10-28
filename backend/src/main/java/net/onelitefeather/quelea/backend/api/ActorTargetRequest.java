package net.onelitefeather.quelea.backend.api;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Serdeable
public record ActorTargetRequest(@NotNull UUID actorId, @NotNull UUID targetId) {
}
