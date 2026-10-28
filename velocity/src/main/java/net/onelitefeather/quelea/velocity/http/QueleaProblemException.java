package net.onelitefeather.quelea.velocity.http;

/**
 * The service answered with a problem document. Callers switch on {@link #code()}.
 */
public final class QueleaProblemException extends RuntimeException {

    private final String code;
    private final int status;
    private final String detail;

    public QueleaProblemException(String code, int status, String detail) {
        super(code + " (HTTP " + status + ")" + (detail == null ? "" : ": " + detail));
        this.code = code;
        this.status = status;
        this.detail = detail;
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
}
