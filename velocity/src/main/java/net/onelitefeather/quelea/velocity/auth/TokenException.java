package net.onelitefeather.quelea.velocity.auth;

import java.io.IOException;

/**
 * No access token could be obtained. An {@link IOException} so that it travels like any other transport failure
 * and the gateway maps it to "party service unavailable".
 */
public final class TokenException extends IOException {

    public TokenException(String message) {
        super(message);
    }

    public TokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
