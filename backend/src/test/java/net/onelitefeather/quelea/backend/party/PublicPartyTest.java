package net.onelitefeather.quelea.backend.party;

import net.onelitefeather.quelea.backend.event.PartyEvent;
import net.onelitefeather.quelea.backend.problem.PartyException;
import net.onelitefeather.quelea.backend.problem.PartyProblem;
import net.onelitefeather.quelea.backend.support.PartyFixture;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Public parties: direct joins, their own size limit, and the leader's control of the flag. */
class PublicPartyTest {

    @Test
    void publicJoinAddsTheMemberAndPublishesJoined() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID joiner = f.online("Bravo");
        Party party = f.partyService.create(leader);
        f.partyService.setPublic(party.id(), leader, true, Precondition.NONE);

        Party after = f.partyService.join(joiner, "Alpha");

        assertTrue(after.isMember(joiner), "a public join must make the player a member");
        assertEquals(1, count(f, "party.joined"), "a public join must publish party.joined");
    }

    @Test
    void requestOnAPublicPartyJoinsDirectlyWithoutHoldingARequest() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID requester = f.online("Bravo");
        Party party = f.partyService.create(leader);
        f.partyService.setPublic(party.id(), leader, true, Precondition.NONE);

        JoinOutcome outcome = f.partyService.requestJoin(requester, "Alpha");

        JoinOutcome.Joined joined = assertInstanceOf(JoinOutcome.Joined.class, outcome);
        assertTrue(joined.party().isMember(requester), "the requester must be a member after a request on a public party");
        assertEquals(0, count(f, "party.join-requested"), "no join request may be announced for a public party");
    }

    @Test
    void joinOnAPrivatePartyIsPartyNotPublic() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID joiner = f.online("Bravo");
        f.partyService.create(leader);

        assertProblem(PartyProblem.PARTY_NOT_PUBLIC, () -> f.partyService.join(joiner, "Alpha"));
        assertProblem(PartyProblem.NOT_IN_PARTY, () -> f.partyService.partyOf(joiner));
    }

    @Test
    void publicPartyRefusesTheTwentyFirstMember() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        Party party = f.partyService.create(leader);
        f.partyService.setPublic(party.id(), leader, true, Precondition.NONE);
        for (int i = 1; i < 20; i++) {
            f.partyService.join(f.online("Player" + i), "Alpha");
        }
        UUID overflow = f.online("Overflow");

        assertProblem(PartyProblem.PARTY_FULL, () -> f.partyService.join(overflow, "Alpha"));
        assertEquals(20, f.partyService.get(party.id()).members().size(), "a full public party must keep its 20 members");
    }

    @Test
    void privateLimitStaysAtTheConfiguredMaximum() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        UUID charlie = f.online("Charlie");
        f.online("Delta");
        Party party = f.partyService.create(leader);
        f.partyService.invite(leader, "Bravo");
        f.partyService.accept(bravo, party.id());
        f.partyService.invite(leader, "Charlie");
        f.partyService.accept(charlie, party.id());

        // The fixture's private limit is 3 members; a private party is full at that size.
        assertProblem(PartyProblem.PARTY_FULL, () -> f.partyService.invite(leader, "Delta"));
        assertEquals(3, f.partyService.get(party.id()).members().size());
        assertFalse(f.partyService.get(party.id()).publicParty());
    }

    @Test
    void closedPartyAbovePrivateLimitKeepsItsMembersAndRejectsNewOnes() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        Party party = f.partyService.create(leader);
        f.partyService.setPublic(party.id(), leader, true, Precondition.NONE);
        for (int i = 1; i < 12; i++) {
            f.partyService.join(f.online("Player" + i), "Alpha");
        }
        f.partyService.setPublic(party.id(), leader, false, Precondition.NONE);
        UUID invited = f.online("Invited");

        assertProblem(PartyProblem.PARTY_FULL, () -> f.partyService.invite(leader, "Invited"));
        assertEquals(12, f.partyService.get(party.id()).members().size(), "closing must keep all 12 members");
    }

    @Test
    void onlyTheLeaderMayOpenTheParty() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID member = f.online("Bravo");
        Party party = f.partyService.create(leader);
        f.partyService.invite(leader, "Bravo");
        f.partyService.accept(member, party.id());

        assertProblem(PartyProblem.NOT_LEADER, () -> f.partyService.setPublic(party.id(), member, true, Precondition.NONE));
    }

    @Test
    void leaderClosesThePartyWithoutAnyPermissionCheck() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        Party party = f.partyService.create(leader);
        f.partyService.setPublic(party.id(), leader, true, Precondition.NONE);

        Party closed = f.partyService.setPublic(party.id(), leader, false, Precondition.NONE);

        assertFalse(closed.publicParty(), "the leader must be able to close the party");
    }

    @Test
    void setPublicBumpsTheVersion() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        Party party = f.partyService.create(leader);
        long before = party.version();

        Party opened = f.partyService.setPublic(party.id(), leader, true, Precondition.NONE);

        assertEquals(before + 1, opened.version(), "opening must bump the version");
    }

    @Test
    void promotionClosesThePublicParty() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID member = f.online("Bravo");
        Party party = f.partyService.create(leader);
        f.partyService.invite(leader, "Bravo");
        f.partyService.accept(member, party.id());
        f.partyService.setPublic(party.id(), leader, true, Precondition.NONE);

        Party promoted = f.partyService.promote(party.id(), leader, member);

        assertEquals(member, promoted.leaderId());
        assertFalse(promoted.publicParty(), "a change of leader must close the party");
    }

    @Test
    void leaderLeavingClosesThePublicParty() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID first = f.online("Bravo");
        UUID second = f.online("Charlie");
        Party party = f.partyService.create(leader);
        f.partyService.invite(leader, "Bravo");
        f.partyService.accept(first, party.id());
        f.partyService.invite(leader, "Charlie");
        f.partyService.accept(second, party.id());
        f.partyService.setPublic(party.id(), leader, true, Precondition.NONE);

        f.partyService.leave(leader, "leave");

        Party after = f.partyService.get(party.id());
        assertEquals(first, after.leaderId(), "the longest-standing member must succeed the leader");
        assertFalse(after.publicParty(), "automatic succession must close the party");
    }

    @Test
    void publicPartyStaysPublicWhenItDropsToOneMemberThroughANonLeaderLeave() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID member = f.online("Bravo");
        Party party = f.partyService.create(leader);
        f.partyService.invite(leader, "Bravo");
        f.partyService.accept(member, party.id());
        f.partyService.setPublic(party.id(), leader, true, Precondition.NONE);

        f.partyService.leave(member, "leave");

        Party after = f.partyService.get(party.id());
        assertEquals(1, after.members().size());
        assertTrue(after.publicParty(), "a non-leader leave must keep the party public");
    }

    @Test
    void memberJoiningKeepsThePartyPublic() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID joiner = f.online("Bravo");
        Party party = f.partyService.create(leader);
        f.partyService.setPublic(party.id(), leader, true, Precondition.NONE);

        f.partyService.join(joiner, "Alpha");

        assertTrue(f.partyService.get(party.id()).publicParty(), "a member change must keep the flag");
    }

    @Test
    void promotionOfAPublicPartyCarriesWasPublicOnLeaderChanged() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID member = f.online("Bravo");
        Party party = f.partyService.create(leader);
        f.partyService.invite(leader, "Bravo");
        f.partyService.accept(member, party.id());
        f.partyService.setPublic(party.id(), leader, true, Precondition.NONE);

        f.partyService.promote(party.id(), leader, member);

        assertEquals(true, lastEvent(f, "party.leader-changed").payload().get("wasPublic"),
                "a promotion that closed the party must say so");
    }

    @Test
    void promotionOfAPrivatePartyOmitsWasPublic() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID member = f.online("Bravo");
        Party party = f.partyService.create(leader);
        f.partyService.invite(leader, "Bravo");
        f.partyService.accept(member, party.id());

        f.partyService.promote(party.id(), leader, member);

        assertFalse(lastEvent(f, "party.leader-changed").payload().containsKey("wasPublic"),
                "wasPublic must be omitted when the flag was not cleared");
    }

    @Test
    void successionOfAPublicPartyCarriesWasPublicOnLeaderChanged() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID member = f.online("Bravo");
        Party party = f.partyService.create(leader);
        f.partyService.invite(leader, "Bravo");
        f.partyService.accept(member, party.id());
        f.partyService.setPublic(party.id(), leader, true, Precondition.NONE);

        f.partyService.leave(leader, "leave");

        assertEquals(true, lastEvent(f, "party.leader-changed").payload().get("wasPublic"),
                "an automatic succession that closed the party must say so");
    }

    @Test
    void successionOfAPrivatePartyOmitsWasPublic() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID member = f.online("Bravo");
        Party party = f.partyService.create(leader);
        f.partyService.invite(leader, "Bravo");
        f.partyService.accept(member, party.id());

        f.partyService.leave(leader, "leave");

        assertFalse(lastEvent(f, "party.leader-changed").payload().containsKey("wasPublic"));
    }

    private static PartyEvent lastEvent(PartyFixture f, String type) {
        List<PartyEvent> matching = f.events.events().stream().filter(e -> e.type().equals(type)).toList();
        assertTrue(!matching.isEmpty(), "no event of type " + type);
        return matching.get(matching.size() - 1);
    }

    private static long count(PartyFixture f, String type) {
        return f.events.types().stream().filter(type::equals).count();
    }

    private static void assertProblem(PartyProblem expected, org.junit.jupiter.api.function.Executable action) {
        PartyException thrown = assertThrows(PartyException.class, action, "expected " + expected.code());
        assertEquals(expected, thrown.problem(), "wrong problem for the rule that was broken");
    }
}
