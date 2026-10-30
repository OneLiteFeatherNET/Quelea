package net.onelitefeather.quelea.backend.api;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A player's join or request, naming a player whose party is the target. */
@Serdeable
public record TargetNameRequest(@NotBlank @Size(max = 16) String targetName) {
}
