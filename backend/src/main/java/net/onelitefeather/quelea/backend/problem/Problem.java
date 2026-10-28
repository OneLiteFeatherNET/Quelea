package net.onelitefeather.quelea.backend.problem;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;

import java.net.URI;
import java.util.List;

/**
 * RFC 9457 problem details. Serialised as {@value #MEDIA_TYPE}; {@code type} points at the section of
 * docs/problems.md that describes {@code code}.
 */
@Serdeable
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Problem(
        URI type,
        String title,
        int status,
        String detail,
        String instance,
        String code,
        String traceId,
        List<FieldError> errors
) {

    public static final String MEDIA_TYPE = "application/problem+json";
    public static final String TYPE_BASE = "https://github.com/OneLiteFeatherNET/Quelea/blob/main/docs/problems.md#";

    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FieldError(String field, String message) {
    }
}
