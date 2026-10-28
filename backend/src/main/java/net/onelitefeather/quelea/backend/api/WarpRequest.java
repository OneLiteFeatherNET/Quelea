package net.onelitefeather.quelea.backend.api;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Serdeable
public record WarpRequest(@NotNull UUID actorId,
                          @NotBlank @Size(max = 64) String server,
                          @Nullable @Size(max = 64)
                          @Schema(description = "The server the party leader left. Members on this server follow the warp "
                                  + "even when they are in a game. Omitted by clients that do not send it.")
                          String fromServer) {
}
