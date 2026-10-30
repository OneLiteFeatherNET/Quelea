package net.onelitefeather.quelea.velocity.http;

import java.util.Optional;

/**
 * The service answered with a problem document. Callers switch on {@link #code()}.
 */
public final class QueleaProblemException extends RuntimeException {

    private final String code;
    private final int status;
    private final String detail;
    private final Long retryAfterSeconds;

    public QueleaProblemException(String code, int status, String detail) {
        this(code, status, detail, null);
    }

    /**
     * @param retryAfterSeconds the whole seconds of the {@code Retry-After} header, or {@code null} when the answer had none
     */
    public QueleaProblemException(String code, int status, String detail, Long retryAfterSeconds) {
        super(code + " (HTTP " + status + ")" + (detail == null ? "" : ": " + detail));
        this.code = code;
        this.status = status;
        this.detail = detail;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * @return the stable problem code, for example {@code not-leader}
     */
    public String code() {
        return code;
    }

    /**
     * @return the HTTP status of the answer
     */
    public int status() {
        return status;
    }

    /**
     * @return the service's human-readable detail, may be {@code null}
     */
    public String detail() {
        return detail;
    }

    /**
     * @return the seconds the service asked the client to wait, when the answer carried a {@code Retry-After} header
     */
    public Optional<Long> retryAfterSeconds() {
        return Optional.ofNullable(retryAfterSeconds);
    }
}
