package net.onelitefeather.quelea.backend.party;

import net.onelitefeather.quelea.backend.event.PartyEvent;
import net.onelitefeather.quelea.backend.problem.PartyException;
import net.onelitefeather.quelea.backend.problem.PartyProblem;
import net.onelitefeather.quelea.backend.support.PartyFixture;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyExpiryJobTest {

    @Test
    void partyIsNotDisbandedBeforeTheAloneTimeout() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        Party party = f.partyService.create(alpha);
        f.clock.advance(Duration.ofMinutes(29));

        job(f).sweep();

        assertEquals(party.id(), f.partyService.partyOf(alpha).id(), "a party must survive until the alone timeout has passed");
    }

    @Test
    void lonelyPartyIsDisbandedWithReasonExpiredAloneAfterTheTimeout() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        f.partyService.create(alpha);
        f.clock.advance(Duration.ofMinutes(30));

        job(f).sweep();

        assertEquals(PartyProblem.NOT_IN_PARTY, problemOf(() -> f.partyService.partyOf(alpha)), "the expiry must release the creator");
        PartyEvent disbanded = lastEvent(f, "party.disbanded");
        assertEquals("expired-alone", disbanded.payload().get("reason"), "the disband must name the expiry as its reason");
    }

    @Test
    void warningIsPublishedOnceBeforeTheExpiry() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        Instant created = f.clock.instant();
        f.partyService.create(alpha);
        f.clock.advance(Duration.ofMinutes(25));

        job(f).sweep();
        job(f).sweep();

        assertEquals(1, count(f, "party.expiring"), "the warning must be published exactly once");
        assertEquals(created.plus(Duration.ofMinutes(30)), lastEvent(f, "party.expiring").payload().get("expiresAt"),
                "the warning must carry the expiry time");
    }

    @Test
    void zeroWarningDisablesTheWarningButNotTheExpiry() {
        PartyFixture f = new PartyFixture();
        f.partySettings.setAloneWarning(Duration.ZERO);
        UUID alpha = f.online("Alpha");
        f.partyService.create(alpha);
        f.clock.advance(Duration.ofMinutes(30));

        job(f).sweep();

        assertEquals(0, count(f, "party.expiring"), "a zero warning must publish no warning");
        assertEquals("expired-alone", lastEvent(f, "party.disbanded").payload().get("reason"), "the party must still expire");
    }

    @Test
    void secondMemberInTimeKeepsThePartyAlive() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        Party party = f.partyService.create(alpha);
        f.clock.advance(Duration.ofMinutes(20));
        Invite invite = f.partyService.invite(alpha, "Bravo");
        f.partyService.accept(bravo, invite.partyId());
        f.clock.advance(Duration.ofMinutes(30));

        job(f).sweep();

        assertEquals(2, f.partyService.get(party.id()).members().size(), "a party with two members must not expire");
        assertFalse(f.events.types().contains("party.disbanded"), "no disband may be published for a party with two members");
        assertFalse(f.events.types().contains("party.expiring"), "no warning may be published once the party has two members");
    }

    @Test
    void invitedPartyWithoutExplicitCreateExpires() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        f.online("Bravo");
        f.partyService.invite(alpha, "Bravo");
        f.clock.advance(Duration.ofMinutes(30));

        job(f).sweep();

        assertEquals("expired-alone", lastEvent(f, "party.disbanded").payload().get("reason"),
                "the party created by the first invite must expire like an explicit one");
    }

    @Test
    void secondRunAfterTheExpiryDisbandsNothingMore() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        f.partyService.create(alpha);
        f.clock.advance(Duration.ofMinutes(30));

        job(f).sweep();
        job(f).sweep();

        assertEquals(1, count(f, "party.disbanded"), "a party must be disbanded exactly once, even when two runs see it due");
    }

    private static PartyExpiryJob job(PartyFixture f) {
        return new PartyExpiryJob(f.locks, f.parties, f.partyService, f.clock);
    }

    private static long count(PartyFixture f, String type) {
        return f.events.types().stream().filter(type::equals).count();
    }

    private static PartyEvent lastEvent(PartyFixture f, String type) {
        List<PartyEvent> matching = f.events.events().stream().filter(e -> e.type().equals(type)).toList();
        assertTrue(!matching.isEmpty(), "no event of type " + type + " was published: " + f.events.types());
        return matching.get(matching.size() - 1);
    }

    private static PartyProblem problemOf(Runnable action) {
        return assertThrows(PartyException.class, action::run).problem();
    }
}
