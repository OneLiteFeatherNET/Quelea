package net.onelitefeather.quelea.backend.api;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** The leader's answer to a join request, naming the requester as the backend resolves names. */
@Serdeable
public record JoinAnswerRequest(@NotNull UUID actorId, @NotBlank @Size(max = 16) String requesterName) {
}
