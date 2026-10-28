package net.onelitefeather.quelea.backend.party;

import net.onelitefeather.quelea.backend.problem.PartyException;
import net.onelitefeather.quelea.backend.problem.PartyProblem;

/**
 * The {@code If-Match} condition of a leader command. It is checked by {@link PartyService} under the party lock,
 * after the party is loaded and before any rule of the command runs, so a stale client changes nothing.
 *
 * @param ifMatch the header value, or {@code null} when the request carried no condition
 */
public record Precondition(String ifMatch) {

    /** No condition: the command runs unconditionally. */
    public static final Precondition NONE = new Precondition(null);

    /**
     * @throws PartyException with {@link PartyProblem#PRECONDITION_FAILED} when the party is not the one the client read
     */
    public void check(Party party) {
        if (ifMatch != null && !EntityTags.matchesStrong(ifMatch, party.version())) {
            throw new PartyException(PartyProblem.PRECONDITION_FAILED,
                    "The party changed since it was read; it is now at version " + party.version() + ".");
        }
    }
}
