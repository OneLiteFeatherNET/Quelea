package net.onelitefeather.quelea.velocity.http;

/**
 * The service could not be reached, answered without a problem document, or answered something unreadable.
 */
public final class QueleaUnavailableException extends RuntimeException {

    public QueleaUnavailableException(String message) {
        super(message);
    }

    public QueleaUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
