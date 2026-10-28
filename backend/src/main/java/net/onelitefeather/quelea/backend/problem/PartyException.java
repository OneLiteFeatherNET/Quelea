package net.onelitefeather.quelea.backend.problem;

/** A domain rule was violated. The handler turns it into the Problem Details response of {@link #problem()}. */
public class PartyException extends RuntimeException {

    private final PartyProblem problem;

    public PartyException(PartyProblem problem, String detail) {
        super(detail);
        this.problem = problem;
    }

    public PartyException(PartyProblem problem) {
        this(problem, problem.title());
    }

    public PartyProblem problem() {
        return problem;
    }
}
