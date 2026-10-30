package net.onelitefeather.quelea.backend.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** The leader's open or close command; the JSON key of the flag is {@code public}. */
@Serdeable
public record PublicPartyRequest(@NotNull UUID actorId, @JsonProperty("public") boolean open) {
}
