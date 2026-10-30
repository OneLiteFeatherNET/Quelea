package net.onelitefeather.quelea.backend.party;

import net.onelitefeather.quelea.backend.event.PartyEvent;
import net.onelitefeather.quelea.backend.problem.PartyException;
import net.onelitefeather.quelea.backend.problem.PartyProblem;
import net.onelitefeather.quelea.backend.support.PartyFixture;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Join requests to a private party: the leader answers, a request expires, and the cooldown after a denial or expiry. */
class JoinRequestTest {

    @Test
    void acceptedRequestAddsTheRequesterToThePrivateParty() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID requester = f.online("Bravo");
        Party party = f.partyService.create(leader);
        f.partyService.requestJoin(requester, "Alpha");

        Party after = f.partyService.acceptRequest(party.id(), leader, "Bravo");

        assertTrue(after.isMember(requester), "the accepted requester must be a member");
        assertProblem(PartyProblem.JOIN_REQUEST_NOT_FOUND, () -> f.partyService.acceptRequest(party.id(), leader, "Bravo"));
    }

    @Test
    void deniedRequestLeavesTheRequesterInNoParty() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID requester = f.online("Bravo");
        Party party = f.partyService.create(leader);
        f.partyService.requestJoin(requester, "Alpha");

        f.partyService.rejectRequest(party.id(), leader, "Bravo");

        assertProblem(PartyProblem.NOT_IN_PARTY, () -> f.partyService.partyOf(requester));
    }

    @Test
    void acceptingAnExpiredRequestIsJoinRequestNotFoundAndLeavesTheParty() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID requester = f.online("Bravo");
        Party party = f.partyService.create(leader);
        f.partyService.requestJoin(requester, "Alpha");

        f.clock.advance(Duration.ofSeconds(60));

        assertProblem(PartyProblem.JOIN_REQUEST_NOT_FOUND, () -> f.partyService.acceptRequest(party.id(), leader, "Bravo"));
        assertEquals(1, f.partyService.get(party.id()).members().size(), "an expired request must not change the party");
    }

    @Test
    void secondRequestWhilePendingIsJoinRequestPending() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID requester = f.online("Bravo");
        f.partyService.create(leader);
        f.partyService.requestJoin(requester, "Alpha");

        assertProblem(PartyProblem.JOIN_REQUEST_PENDING, () -> f.partyService.requestJoin(requester, "Alpha"));
    }

    @Test
    void requesterAlreadyInAPartyIsAlreadyInParty() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID requester = f.online("Bravo");
        f.partyService.create(leader);
        f.partyService.create(requester);

        assertProblem(PartyProblem.ALREADY_IN_PARTY, () -> f.partyService.requestJoin(requester, "Alpha"));
    }

    @Test
    void targetWithoutPartyIsTargetNotInParty() {
        PartyFixture f = new PartyFixture();
        UUID requester = f.online("Bravo");
        f.online("Alpha");

        assertProblem(PartyProblem.TARGET_NOT_IN_PARTY, () -> f.partyService.requestJoin(requester, "Alpha"));
    }

    @Test
    void offlineTargetIsPlayerNotOnline() {
        PartyFixture f = new PartyFixture();
        UUID requester = f.online("Bravo");

        assertProblem(PartyProblem.PLAYER_NOT_ONLINE, () -> f.partyService.requestJoin(requester, "Nobody"));
    }

    @Test
    void answerByAMemberIsNotLeader() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID member = f.online("Charlie");
        UUID requester = f.online("Bravo");
        Party party = f.partyService.create(leader);
        f.partyService.invite(leader, "Charlie");
        f.partyService.accept(member, party.id());
        f.partyService.requestJoin(requester, "Alpha");

        assertProblem(PartyProblem.NOT_LEADER, () -> f.partyService.acceptRequest(party.id(), member, "Bravo"));
    }

    @Test
    void requestPublishesJoinRequestedWithRequesterAndExpiry() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID requester = f.online("Bravo");
        f.partyService.create(leader);

        f.partyService.requestJoin(requester, "Alpha");

        PartyEvent event = lastEvent(f, "party.join-requested");
        assertEquals(requester, event.payload().get("requesterId"));
        assertEquals("Bravo", event.payload().get("requesterName"));
        assertEquals(f.clock.instant().plus(Duration.ofSeconds(60)), event.payload().get("expiresAt"));
    }

    @Test
    void acceptPublishesAnsweredAcceptedAndJoined() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID requester = f.online("Bravo");
        Party party = f.partyService.create(leader);
        f.partyService.requestJoin(requester, "Alpha");

        f.partyService.acceptRequest(party.id(), leader, "Bravo");

        assertEquals("accepted", lastEvent(f, "party.join-request-answered").payload().get("outcome"));
        assertEquals(1, count(f, "party.joined"), "an accepted request must publish party.joined");
    }

    @Test
    void rejectPublishesAnsweredDenied() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID requester = f.online("Bravo");
        Party party = f.partyService.create(leader);
        f.partyService.requestJoin(requester, "Alpha");

        f.partyService.rejectRequest(party.id(), leader, "Bravo");

        assertEquals("denied", lastEvent(f, "party.join-request-answered").payload().get("outcome"));
    }

    // --- Cooldown -------------------------------------------------------------------------------

    @Test
    void cooldownAfterDenialStillRefusesOneSecondBeforeItEnds() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID requester = f.online("Bravo");
        Party party = f.partyService.create(leader);
        f.partyService.requestJoin(requester, "Alpha");
        f.partyService.rejectRequest(party.id(), leader, "Bravo");

        f.clock.advance(Duration.ofSeconds(59));

        PartyException thrown = assertThrows(PartyException.class, () -> f.partyService.requestJoin(requester, "Alpha"));
        assertEquals(PartyProblem.JOIN_REQUEST_COOLDOWN, thrown.problem());
        assertEquals(Duration.ofSeconds(1), thrown.retryAfter().orElseThrow(), "Retry-After must be the whole seconds left");
    }

    @Test
    void cooldownAfterDenialAllowsANewRequestExactlyWhenItEnds() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID requester = f.online("Bravo");
        Party party = f.partyService.create(leader);
        f.partyService.requestJoin(requester, "Alpha");
        f.partyService.rejectRequest(party.id(), leader, "Bravo");

        f.clock.advance(Duration.ofSeconds(60));

        assertInstanceOf(JoinOutcome.Requested.class, f.partyService.requestJoin(requester, "Alpha"));
    }

    @Test
    void cooldownAfterExpiryStillRefusesOneSecondBeforeItEnds() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID requester = f.online("Bravo");
        f.partyService.create(leader);
        f.partyService.requestJoin(requester, "Alpha");

        f.clock.advance(Duration.ofSeconds(119));

        PartyException thrown = assertThrows(PartyException.class, () -> f.partyService.requestJoin(requester, "Alpha"));
        assertEquals(PartyProblem.JOIN_REQUEST_COOLDOWN, thrown.problem());
        assertEquals(Duration.ofSeconds(1), thrown.retryAfter().orElseThrow());
    }

    @Test
    void cooldownAfterExpiryAllowsANewRequestTwoMinutesAfterTheRequest() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID requester = f.online("Bravo");
        f.partyService.create(leader);
        f.partyService.requestJoin(requester, "Alpha");

        f.clock.advance(Duration.ofSeconds(120));

        assertInstanceOf(JoinOutcome.Requested.class, f.partyService.requestJoin(requester, "Alpha"));
    }

    @Test
    void acceptedRequestLeavesNoCooldownForALaterRequest() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID requester = f.online("Bravo");
        Party party = f.partyService.create(leader);
        f.partyService.requestJoin(requester, "Alpha");
        f.partyService.acceptRequest(party.id(), leader, "Bravo");
        f.partyService.leave(requester, "leave");

        assertInstanceOf(JoinOutcome.Requested.class, f.partyService.requestJoin(requester, "Alpha"));
    }

    private static PartyEvent lastEvent(PartyFixture f, String type) {
        List<PartyEvent> matching = f.events.events().stream().filter(e -> e.type().equals(type)).toList();
        assertTrue(!matching.isEmpty(), "no event of type " + type + " was published: " + f.events.types());
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
