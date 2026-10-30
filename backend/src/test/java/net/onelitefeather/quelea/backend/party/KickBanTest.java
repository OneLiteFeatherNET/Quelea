package net.onelitefeather.quelea.backend.party;

import net.onelitefeather.quelea.backend.problem.PartyException;
import net.onelitefeather.quelea.backend.problem.PartyProblem;
import net.onelitefeather.quelea.backend.support.PartyFixture;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A kicked player is banned from the party until the leader invites them, and the ban ends with the party. */
class KickBanTest {

    @Test
    void kickedPlayerIsBlockedFromJoiningAPublicParty() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID kicked = f.online("Bravo");
        Party party = f.partyService.create(leader);
        f.partyService.invite(leader, "Bravo");
        f.partyService.accept(kicked, party.id());
        f.partyService.kick(party.id(), leader, kicked);
        f.partyService.setPublic(party.id(), leader, true, Precondition.NONE);

        assertProblem(PartyProblem.PARTY_BANNED, () -> f.partyService.join(kicked, "Alpha"));
    }

    @Test
    void kickedPlayerIsBlockedFromRequestingAPrivateParty() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID kicked = f.online("Bravo");
        Party party = f.partyService.create(leader);
        f.partyService.invite(leader, "Bravo");
        f.partyService.accept(kicked, party.id());
        f.partyService.kick(party.id(), leader, kicked);

        assertProblem(PartyProblem.PARTY_BANNED, () -> f.partyService.requestJoin(kicked, "Alpha"));
    }

    @Test
    void inviteLiftsTheBanSoTheInvitedPlayerCanJoin() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID kicked = f.online("Bravo");
        Party party = f.partyService.create(leader);
        f.partyService.invite(leader, "Bravo");
        f.partyService.accept(kicked, party.id());
        f.partyService.kick(party.id(), leader, kicked);

        f.partyService.invite(leader, "Bravo");
        Party after = f.partyService.accept(kicked, party.id());

        assertTrue(after.isMember(kicked), "an explicit invite must lift the ban for the invited player");
    }

    @Test
    void banEndsWithTheParty() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID kicked = f.online("Bravo");
        Party old = f.partyService.create(leader);
        f.partyService.invite(leader, "Bravo");
        f.partyService.accept(kicked, old.id());
        f.partyService.kick(old.id(), leader, kicked);
        f.partyService.disband(old.id(), leader);

        f.partyService.create(leader);
        JoinOutcome outcome = f.partyService.requestJoin(kicked, "Alpha");

        assertInstanceOf(JoinOutcome.Requested.class, outcome, "a ban must not outlive the party it belongs to");
    }

    private static void assertProblem(PartyProblem expected, org.junit.jupiter.api.function.Executable action) {
        PartyException thrown = assertThrows(PartyException.class, action, "expected " + expected.code());
        assertEquals(expected, thrown.problem(), "wrong problem for the rule that was broken");
    }
}
