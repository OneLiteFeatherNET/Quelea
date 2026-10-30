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
    PARTY_NOT_PUBLIC("party-not-public", HttpStatus.CONFLICT, "Party is not public"),
    PARTY_BANNED("party-banned", HttpStatus.FORBIDDEN, "Player is banned from the party"),
    TARGET_NOT_IN_PARTY("target-not-in-party", HttpStatus.NOT_FOUND, "Target is not in a party"),
    JOIN_REQUEST_PENDING("join-request-pending", HttpStatus.CONFLICT, "Join request already pending"),
    JOIN_REQUEST_COOLDOWN("join-request-cooldown", HttpStatus.TOO_MANY_REQUESTS, "Join request cooldown"),
    JOIN_REQUEST_NOT_FOUND("join-request-not-found", HttpStatus.NOT_FOUND, "Join request not found"),
    PLAYER_NOT_ONLINE("player-not-online", HttpStatus.NOT_FOUND, "Player is not online"),
    INVITE_NOT_FOUND("invite-not-found", HttpStatus.NOT_FOUND, "Invite not found"),
    CANNOT_TARGET_SELF("cannot-target-self", HttpStatus.BAD_REQUEST, "The action cannot target yourself"),
    LOCK_TIMEOUT("lock-timeout", HttpStatus.SERVICE_UNAVAILABLE, "The party is busy, try again"),
    UNAUTHORIZED("unauthorized", HttpStatus.UNAUTHORIZED, "Authentication required"),
    FORBIDDEN("forbidden", HttpStatus.FORBIDDEN, "Access denied"),
    PRECONDITION_FAILED("precondition-failed", HttpStatus.PRECONDITION_FAILED, "Precondition failed"),
    IDEMPOTENCY_KEY_REUSED("idempotency-key-reused", HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency key reused"),
    REQUEST_IN_PROGRESS("request-in-progress", HttpStatus.CONFLICT, "Request in progress"),
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
