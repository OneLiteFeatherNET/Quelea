package net.onelitefeather.quelea.backend.problem;

import io.micronaut.http.HttpStatus;

/**
 * Every error code the service can return. The {@link #code()} is the stable slug clients switch on and
 * the anchor in docs/problems.md.
 */
public enum PartyProblem {
    VALIDATION_FAILED("validation-failed", HttpStatus.BAD_REQUEST, "Validation failed"),
    NOT_FOUND("not-found", HttpStatus.NOT_FOUND, "Not found"),
    METHOD_NOT_ALLOWED("method-not-allowed", HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    UNSUPPORTED_MEDIA_TYPE("unsupported-media-type", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
    PARTY_NOT_FOUND("party-not-found", HttpStatus.NOT_FOUND, "Party not found"),
    NOT_IN_PARTY("not-in-party", HttpStatus.NOT_FOUND, "Player is not in a party"),
    ALREADY_IN_PARTY("already-in-party", HttpStatus.CONFLICT, "Player is already in a party"),
    NOT_LEADER("not-leader", HttpStatus.FORBIDDEN, "Only the party leader may do this"),
    NOT_MEMBER("not-member", HttpStatus.FORBIDDEN, "Only party members may do this"),
    PARTY_FULL("party-full", HttpStatus.CONFLICT, "Party is full"),
    PLAYER_NOT_ONLINE("player-not-online", HttpStatus.NOT_FOUND, "Player is not online"),
    INVITE_NOT_FOUND("invite-not-found", HttpStatus.NOT_FOUND, "Invite not found"),
    CANNOT_TARGET_SELF("cannot-target-self", HttpStatus.BAD_REQUEST, "The action cannot target yourself"),
    LOCK_TIMEOUT("lock-timeout", HttpStatus.SERVICE_UNAVAILABLE, "The party is busy, try again"),
    INTERNAL_ERROR("internal-error", HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");

    private final String code;
    private final HttpStatus status;
    private final String title;

    PartyProblem(String code, HttpStatus status, String title) {
        this.code = code;
        this.status = status;
        this.title = title;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
