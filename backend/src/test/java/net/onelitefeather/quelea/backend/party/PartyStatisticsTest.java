package net.onelitefeather.quelea.backend.party;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.onelitefeather.quelea.backend.support.PartyFixture;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PartyStatisticsTest {

    @Test
    void creationIncrementsCreatedCounter() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");

        f.partyService.create(leader);

        assertEquals(1.0, f.meters.get("quelea.parties.created").counter().count(), "one create must count one party");
    }

    @Test
    void leaderDisbandCountsReasonLeaderAndRecordsLifetime() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        Party party = f.partyService.create(leader);
        f.clock.advance(Duration.ofMinutes(4));

        f.partyService.disband(party.id(), leader);

        assertEquals(1.0, disbanded(f.meters, "leader"), "a leader disband must count reason leader");
        assertEquals(1L, lifetime(f.meters, "leader").count(), "a leader disband must record one lifetime");
        assertEquals(4.0, lifetime(f.meters, "leader").totalTime(TimeUnit.MINUTES), 0.001, "the lifetime must be 4 minutes");
    }

    @Test
    void lastMemberLeavingCountsReasonLastMemberLeftWithLifetime() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        f.partyService.create(leader);
        Invite invite = f.partyService.invite(leader, "Bravo");
        f.partyService.accept(bravo, invite.partyId());
        f.clock.advance(Duration.ofMinutes(10));

        f.partyService.leave(bravo, "quit");

        assertEquals(1.0, disbanded(f.meters, "last-member-left"), "the party must count reason last-member-left");
        assertEquals(10.0, lifetime(f.meters, "last-member-left").totalTime(TimeUnit.MINUTES), 0.001,
                "the lifetime must be 10 minutes since creation");
    }

    @Test
    void expiryAfterThirtyMinutesCountsExpiredAloneWithThirtyMinuteLifetime() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        Party party = f.partyService.create(leader);
        f.clock.advance(Duration.ofMinutes(30));

        f.partyService.expire(party.id());

        assertEquals(1.0, disbanded(f.meters, "expired-alone"), "an expired party must count reason expired-alone");
        assertEquals(1L, lifetime(f.meters, "expired-alone").count(), "an expired party must record one lifetime");
        assertEquals(30.0, lifetime(f.meters, "expired-alone").totalTime(TimeUnit.MINUTES), 0.001,
                "the recorded lifetime must be the 30 minutes since creation");
    }

    @Test
    void joinRecordsSizeAfterEachJoin() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        UUID charlie = f.online("Charlie");
        f.partyService.create(leader);
        Invite first = f.partyService.invite(leader, "Bravo");
        f.partyService.accept(bravo, first.partyId());

        assertEquals(1L, size(f.meters).count(), "the first join must record one size");
        assertEquals(2.0, size(f.meters).max(), 0.001, "the size after the first join must be 2");

        Invite second = f.partyService.invite(leader, "Charlie");
        f.partyService.accept(charlie, second.partyId());

        assertEquals(2L, size(f.meters).count(), "the second join must record a second size");
        assertEquals(3.0, size(f.meters).max(), 0.001, "the size after the second join must be 3");
        assertEquals(5.0, size(f.meters).totalAmount(), 0.001, "the recorded sizes must be 2 and 3");
    }

    private static double disbanded(SimpleMeterRegistry meters, String reason) {
        return meters.get("quelea.parties.disbanded").tag("reason", reason).counter().count();
    }

    private static Timer lifetime(SimpleMeterRegistry meters, String reason) {
        return meters.get("quelea.party.lifetime").tag("reason", reason).timer();
    }

    private static DistributionSummary size(SimpleMeterRegistry meters) {
        return meters.get("quelea.party.size").summary();
    }
}
