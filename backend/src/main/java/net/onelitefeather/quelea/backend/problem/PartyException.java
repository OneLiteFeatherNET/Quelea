package net.onelitefeather.quelea.backend.problem;

import java.time.Duration;
import java.util.Optional;

/**
 * A domain rule was violated. The handler turns it into the Problem Details response of {@link #problem()}. A rule
 * that asks the client to wait carries the wait as {@link #retryAfter()}, sent as the {@code Retry-After} header.
 */
public class PartyException extends RuntimeException {

    private final PartyProblem problem;
    private final Duration retryAfter;

    public PartyException(PartyProblem problem, String detail) {
        this(problem, detail, null);
    }

    public PartyException(PartyProblem problem, String detail, Duration retryAfter) {
        super(detail);
        this.problem = problem;
        this.retryAfter = retryAfter;
    }

    public PartyException(PartyProblem problem) {
        this(problem, problem.title());
    }

    public PartyProblem problem() {
        return problem;
    }

    /** The whole seconds the client must wait, when the rule is a cooldown. */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
