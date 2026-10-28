package net.onelitefeather.quelea.backend.api;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Serdeable
public record LoginRequest(@NotBlank @Size(max = 16) String name) {
}
