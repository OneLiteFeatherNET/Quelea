package net.onelitefeather.quelea.velocity.http;

import java.util.OptionalInt;

/**
 * The service could not be reached, answered without a problem document, or answered something unreadable. When the
 * service answered at all, {@link #httpStatus()} names the status.
 */
public final class QueleaUnavailableException extends RuntimeException {

    private final Integer httpStatus;

    public QueleaUnavailableException(String message) {
        super(message);
        this.httpStatus = null;
    }

    public QueleaUnavailableException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = null;
    }

    /**
     * @param httpStatus the status of an answer that had no problem document
     */
    public QueleaUnavailableException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    /**
     * @return the HTTP status when the service answered, empty for connection failures and unreadable bodies
     */
    public OptionalInt httpStatus() {
        return httpStatus == null ? OptionalInt.empty() : OptionalInt.of(httpStatus);
    }
}
