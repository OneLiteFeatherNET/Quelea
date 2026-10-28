package net.onelitefeather.quelea.backend.party;

import net.onelitefeather.quelea.backend.event.PartyEvent;
import net.onelitefeather.quelea.backend.problem.PartyException;
import net.onelitefeather.quelea.backend.problem.PartyProblem;
import net.onelitefeather.quelea.backend.support.PartyFixture;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyServiceTest {

    // --- One party per player -------------------------------------------------------------------

    @Test
    void concurrentAcceptsLetExactlyOneSucceed() throws Exception {
        PartyFixture f = new PartyFixture();
        UUID leaderA = f.online("Alpha");
        UUID leaderB = f.online("Bravo");
        UUID player = f.online("Charlie");
        Party partyA = f.partyService.create(leaderA);
        Party partyB = f.partyService.create(leaderB);
        f.partyService.invite(leaderA, "Charlie");
        f.partyService.invite(leaderB, "Charlie");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = pool.submit(attempt(f, start, player, partyA.id()));
            Future<String> second = pool.submit(attempt(f, start, player, partyB.id()));
            start.countDown();
            List<String> outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertEquals(1, outcomes.stream().filter("ok"::equals).count(), "exactly one accept must succeed: " + outcomes);
            assertEquals(1, outcomes.stream().filter("already-in-party"::equals).count(), "the other accept must answer already-in-party: " + outcomes);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void createRejectedWhenLeaderIsAlreadyInParty() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        f.partyService.create(leader);

        assertProblem(PartyProblem.ALREADY_IN_PARTY, () -> f.partyService.create(leader));
    }

    // --- Creating and reading -------------------------------------------------------------------

    @Test
    void createMakesOnlineLeaderTheOnlyMember() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");

        Party party = f.partyService.create(leader);

        assertEquals(leader, party.leaderId(), "the creator must lead the new party");
        assertEquals(List.of(leader), party.members().stream().map(Member::id).toList(), "the creator must be the only member");
    }

    @Test
    void createRejectedWhenLeaderIsOffline() {
        PartyFixture f = new PartyFixture();

        assertProblem(PartyProblem.PLAYER_NOT_ONLINE, () -> f.partyService.create(UUID.randomUUID()));
    }

    @Test
    void createPublishesCreatedEvent() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");

        Party party = f.partyService.create(leader);

        assertEquals(List.of("party.created"), f.events.types(), "create must publish exactly one party.created");
        assertEquals(party.id(), f.events.events().get(0).partyId(), "the event must name the new party");
    }

    @Test
    void getUnknownPartyIsPartyNotFound() {
        PartyFixture f = new PartyFixture();

        assertProblem(PartyProblem.PARTY_NOT_FOUND, () -> f.partyService.get(UUID.randomUUID()));
    }

    @Test
    void partyOfPlayerWithoutPartyIsNotInParty() {
        PartyFixture f = new PartyFixture();

        assertProblem(PartyProblem.NOT_IN_PARTY, () -> f.partyService.partyOf(UUID.randomUUID()));
    }

    // --- Invites --------------------------------------------------------------------------------

    @Test
    void inviteWithoutPartyCreatesPartyWithInviterAsLeader() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");

        Invite invite = f.partyService.invite(alpha, "bravo");

        Party party = f.partyService.partyOf(alpha);
        assertEquals(alpha, party.leaderId(), "the inviter must lead the party created for the invite");
        assertEquals(party.id(), invite.partyId(), "the invite must name that party");
        assertEquals(bravo, invite.targetId(), "the invite must target the player found by name");
        assertEquals(1, f.partyService.invitesOf(bravo).size(), "the target must hold the invite");
    }

    @Test
    void inviteByNameIsCaseInsensitive() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        f.online("Bravo");

        Invite invite = f.partyService.invite(alpha, "BRAVO");

        assertEquals("Bravo", invite.targetName(), "the invite must carry the name as the network knows it");
    }

    @Test
    void inviteOfOfflinePlayerIsPlayerNotOnline() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");

        assertProblem(PartyProblem.PLAYER_NOT_ONLINE, () -> f.partyService.invite(alpha, "nobody"));
    }

    @Test
    void inviteOfSelfIsCannotTargetSelf() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");

        assertProblem(PartyProblem.CANNOT_TARGET_SELF, () -> f.partyService.invite(alpha, "Alpha"));
    }

    @Test
    void inviteByNonLeaderIsNotLeader() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        UUID member = f.online("Bravo");
        UUID third = f.online("Charlie");
        join(f, leader, "Bravo");

        assertProblem(PartyProblem.NOT_LEADER, () -> f.partyService.invite(member, "Charlie"));
        assertFalse(f.partyService.invitesOf(third).stream().findAny().isPresent(), "a refused invite must not be stored");
    }

    @Test
    void inviteOfPlayerAlreadyInPartyIsAlreadyInParty() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        f.online("Bravo");
        UUID other = f.online("Charlie");
        f.partyService.create(other);

        f.partyService.create(leader);
        assertProblem(PartyProblem.ALREADY_IN_PARTY, () -> f.partyService.invite(leader, "Charlie"));
    }

    @Test
    void failedFirstInviteLeavesInviterWithoutParty() {
        PartyFixture f = new PartyFixture();
        UUID inviter = f.online("Alpha");
        UUID other = f.online("Charlie");
        f.partyService.create(other);

        assertProblem(PartyProblem.ALREADY_IN_PARTY, () -> f.partyService.invite(inviter, "Charlie"));
        assertProblem(PartyProblem.NOT_IN_PARTY, () -> f.partyService.partyOf(inviter));
    }

    @Test
    void inviteToFullPartyIsPartyFull() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        f.online("Bravo");
        f.online("Charlie");
        join(f, leader, "Bravo");
        join(f, leader, "Charlie");
        f.online("Delta");

        assertProblem(PartyProblem.PARTY_FULL, () -> f.partyService.invite(leader, "Delta"));
    }

    @Test
    void reinviteRefreshesInviteLifetime() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        f.partyService.invite(alpha, "Bravo");
        f.clock.advance(Duration.ofSeconds(50));

        f.partyService.invite(alpha, "Bravo");
        f.clock.advance(Duration.ofSeconds(50));

        assertEquals(1, f.partyService.invitesOf(bravo).size(), "the second invite must still be live 100 s after the first");
    }

    @Test
    void inviteEventNamesInviterAndTargetAndExpiry() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");

        Invite invite = f.partyService.invite(alpha, "Bravo");

        PartyEvent event = lastEvent(f, "party.invited");
        assertEquals(alpha, event.payload().get("inviterId"), "the payload must name the inviter");
        assertEquals("Alpha", event.payload().get("inviterName"), "the payload must name the inviter");
        assertEquals(bravo, event.payload().get("targetId"), "the payload must name the target");
        assertEquals("Bravo", event.payload().get("targetName"), "the payload must name the target");
        assertEquals(invite.expiresAt(), event.payload().get("expiresAt"), "the payload must carry the expiry");
    }

    @Test
    void acceptJoinsPartyAndRemovesInvite() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        Party party = f.partyService.create(alpha);
        f.partyService.invite(alpha, "Bravo");

        Party joined = f.partyService.accept(bravo, party.id());

        assertTrue(joined.isMember(bravo), "the accepting player must be a member afterwards");
        assertEquals(party.id(), f.partyService.partyOf(bravo).id(), "the player must be bound to the party");
        assertTrue(f.partyService.invitesOf(bravo).isEmpty(), "the accepted invite must be removed");
        assertEquals("party.joined", lastEventType(f), "accept must publish party.joined");
    }

    @Test
    void acceptWithoutInviteIsInviteNotFound() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        Party party = f.partyService.create(alpha);

        assertProblem(PartyProblem.INVITE_NOT_FOUND, () -> f.partyService.accept(bravo, party.id()));
    }

    @Test
    void expiredInviteIsInviteNotFound() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        Party party = f.partyService.create(alpha);
        f.partyService.invite(alpha, "Bravo");

        f.clock.advance(Duration.ofSeconds(61));

        assertProblem(PartyProblem.INVITE_NOT_FOUND, () -> f.partyService.accept(bravo, party.id()));
        assertTrue(f.partyService.invitesOf(bravo).isEmpty(), "an expired invite must not be listed");
    }

    @Test
    void acceptIntoFullPartyIsPartyFullAndPartyIsUnchanged() {
        PartyFixture f = new PartyFixture();
        UUID leader = f.online("Alpha");
        f.online("Bravo");
        f.online("Charlie");
        join(f, leader, "Bravo");
        UUID waiting = f.online("Echo");
        f.partyService.invite(leader, "Echo");
        join(f, leader, "Charlie");
        Party before = f.partyService.partyOf(leader);

        assertProblem(PartyProblem.PARTY_FULL, () -> f.partyService.accept(waiting, before.id()));
        assertEquals(before, f.partyService.get(before.id()), "the party must be unchanged");
    }

    @Test
    void acceptWhileInAnotherPartyIsAlreadyInParty() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        Party target = f.partyService.create(alpha);
        f.partyService.invite(alpha, "Bravo");
        Party own = f.partyService.create(bravo);

        assertProblem(PartyProblem.ALREADY_IN_PARTY, () -> f.partyService.accept(bravo, target.id()));
        assertEquals(own.id(), f.partyService.partyOf(bravo).id(), "the player must stay in the own party");
    }

    @Test
    void denyDropsInviteAndPublishesDenied() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        Party party = f.partyService.create(alpha);
        f.partyService.invite(alpha, "Bravo");

        f.partyService.deny(bravo, party.id());

        assertTrue(f.partyService.invitesOf(bravo).isEmpty(), "deny must drop the invite");
        assertEquals("party.denied", lastEventType(f), "deny must publish party.denied");
        assertEquals(bravo, lastEvent(f, "party.denied").payload().get("playerId"), "the event must name the player");
    }

    // --- Leaving and leadership -----------------------------------------------------------------

    @Test
    void leaderLeavingMakesLongestStandingMemberLeader() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        UUID charlie = f.online("Charlie");
        Party party = f.partyService.create(alpha);
        f.partyService.invite(alpha, "Bravo");
        f.partyService.accept(bravo, party.id());
        f.clock.advance(Duration.ofSeconds(5));
        f.partyService.invite(alpha, "Charlie");
        f.partyService.accept(charlie, party.id());

        f.partyService.leave(alpha, "leave");

        Party after = f.partyService.get(party.id());
        assertEquals(bravo, after.leaderId(), "the longest-standing remaining member must lead");
        assertEquals(List.of(bravo, charlie), after.members().stream().map(Member::id).toList(), "the leaver must be gone");
        assertEquals(List.of("party.left", "party.leader-changed"), f.events.types().subList(f.events.types().size() - 2, f.events.types().size()),
                "leaving the leader must publish party.left and then party.leader-changed");
    }

    @Test
    void lastPartnerLeavingDisbandsPartyAndReleasesRemainingMember() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        Party party = f.partyService.create(alpha);
        f.partyService.invite(alpha, "Bravo");
        f.partyService.accept(bravo, party.id());

        f.partyService.leave(bravo, "leave");

        assertProblem(PartyProblem.NOT_IN_PARTY, () -> f.partyService.partyOf(alpha));
        assertProblem(PartyProblem.PARTY_NOT_FOUND, () -> f.partyService.get(party.id()));
        assertEquals("party.disbanded", lastEventType(f), "the party must be disbanded when one member remains");
    }

    @Test
    void lastPartnerLeavingCarriesReasonLastMemberLeft() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        Party party = join(f, alpha, "Bravo");

        f.partyService.leave(bravo, "leave");

        assertEquals("last-member-left", lastEvent(f, "party.disbanded").payload().get("reason"),
                "a party that drops to one member must be disbanded with reason last-member-left, whatever the leave reason");
    }

    @Test
    void disbandByLeaderCarriesReasonLeader() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        Party party = f.partyService.create(alpha);

        f.partyService.disband(party.id(), alpha);

        assertEquals("leader", lastEvent(f, "party.disbanded").payload().get("reason"), "the leader's disband must carry reason leader");
    }

    @Test
    void leaveOfPlayerWithoutPartyIsNotInParty() {
        PartyFixture f = new PartyFixture();

        assertProblem(PartyProblem.NOT_IN_PARTY, () -> f.partyService.leave(UUID.randomUUID(), "leave"));
    }

    @Test
    void leaveEventCarriesReasonAndPlayer() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        UUID charlie = f.online("Charlie");
        join(f, alpha, "Bravo");
        join(f, alpha, "Charlie");

        f.partyService.leave(bravo, "disconnect");

        PartyEvent left = lastEvent(f, "party.left");
        assertEquals(bravo, left.payload().get("playerId"), "the event must name the leaver");
        assertEquals("disconnect", left.payload().get("reason"), "the event must carry the reason");
        assertEquals(List.of(alpha, charlie), left.members().stream().map(Member::id).toList(), "members must be the list after the change");
    }

    // --- Leader-only commands -------------------------------------------------------------------

    @Test
    void kickByNonLeaderIsNotLeader() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        UUID charlie = f.online("Charlie");
        Party party = join(f, alpha, "Bravo");
        join(f, alpha, "Charlie");

        assertProblem(PartyProblem.NOT_LEADER, () -> f.partyService.kick(party.id(), bravo, charlie));
    }

    @Test
    void kickOfSelfIsCannotTargetSelf() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        Party party = f.partyService.create(alpha);

        assertProblem(PartyProblem.CANNOT_TARGET_SELF, () -> f.partyService.kick(party.id(), alpha, alpha));
    }

    @Test
    void kickOfNonMemberIsNotMember() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID outsider = f.online("Bravo");
        Party party = f.partyService.create(alpha);

        assertProblem(PartyProblem.NOT_MEMBER, () -> f.partyService.kick(party.id(), alpha, outsider));
    }

    @Test
    void kickRemovesMemberAndPublishesKicked() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        UUID charlie = f.online("Charlie");
        Party party = join(f, alpha, "Bravo");
        join(f, alpha, "Charlie");

        Party after = f.partyService.kick(party.id(), alpha, bravo);

        assertFalse(after.isMember(bravo), "the kicked player must be removed");
        assertTrue(after.isMember(charlie), "other members must stay");
        assertEquals(bravo, lastEvent(f, "party.kicked").payload().get("playerId"), "the event must name the kicked player");
        assertTrue(after.isLeader(alpha), "the leader must stay leader");
    }

    @Test
    void kickOfLastOtherMemberDisbandsParty() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        Party party = join(f, alpha, "Bravo");

        f.partyService.kick(party.id(), alpha, bravo);

        assertProblem(PartyProblem.PARTY_NOT_FOUND, () -> f.partyService.get(party.id()));
        assertProblem(PartyProblem.NOT_IN_PARTY, () -> f.partyService.partyOf(alpha));
        assertEquals("party.disbanded", lastEventType(f), "a party of one must be disbanded");
    }

    @Test
    void promoteByNonLeaderIsNotLeader() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        UUID charlie = f.online("Charlie");
        Party party = join(f, alpha, "Bravo");
        join(f, alpha, "Charlie");

        assertProblem(PartyProblem.NOT_LEADER, () -> f.partyService.promote(party.id(), bravo, charlie));
    }

    @Test
    void promoteOfSelfIsCannotTargetSelf() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        Party party = f.partyService.create(alpha);

        assertProblem(PartyProblem.CANNOT_TARGET_SELF, () -> f.partyService.promote(party.id(), alpha, alpha));
    }

    @Test
    void promoteMakesMemberLeaderAndKeepsFormerLeaderAsMember() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        Party party = join(f, alpha, "Bravo");

        Party promoted = f.partyService.promote(party.id(), alpha, bravo);

        assertEquals(bravo, promoted.leaderId(), "the target must become leader");
        assertTrue(promoted.isMember(alpha), "the former leader must stay a member");
        assertEquals(alpha, lastEvent(f, "party.leader-changed").payload().get("previousLeaderId"), "the event must name the previous leader");
    }

    @Test
    void disbandByLeaderRemovesPartyAndReleasesMembers() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        Party party = join(f, alpha, "Bravo");

        f.partyService.disband(party.id(), alpha);

        assertProblem(PartyProblem.PARTY_NOT_FOUND, () -> f.partyService.get(party.id()));
        assertProblem(PartyProblem.NOT_IN_PARTY, () -> f.partyService.partyOf(bravo));
        PartyEvent disbanded = lastEvent(f, "party.disbanded");
        assertEquals(2, ((List<?>) disbanded.payload().get("formerMembers")).size(), "the event must list the former members");
    }

    @Test
    void disbandByNonLeaderIsNotLeader() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        Party party = join(f, alpha, "Bravo");

        assertProblem(PartyProblem.NOT_LEADER, () -> f.partyService.disband(party.id(), bravo));
        assertEquals(party.id(), f.partyService.partyOf(bravo).id(), "the party must survive a refused disband");
    }

    @Test
    void warpByLeaderPublishesWarpWithServer() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        Party party = f.partyService.create(alpha);

        f.partyService.warp(party.id(), alpha, "Bounce-2");

        PartyEvent warp = lastEvent(f, "party.warp");
        assertEquals("Bounce-2", warp.payload().get("server"), "the event must carry the target server name");
        assertEquals(alpha, warp.leaderId(), "the event must name the leader");
    }

    @Test
    void warpByMemberIsNotLeader() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        Party party = join(f, alpha, "Bravo");

        assertProblem(PartyProblem.NOT_LEADER, () -> f.partyService.warp(party.id(), bravo, "Bounce-2"));
    }

    // --- Party chat -----------------------------------------------------------------------------

    @Test
    void chatByMemberPublishesChatEvent() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        Party party = join(f, alpha, "Bravo");

        f.partyService.chat(party.id(), bravo, "hello");

        PartyEvent chat = lastEvent(f, "party.chat");
        assertEquals(bravo, chat.payload().get("senderId"), "the event must name the sender");
        assertEquals("Bravo", chat.payload().get("senderName"), "the event must name the sender");
        assertEquals("hello", chat.payload().get("message"), "the event must carry the message");
    }

    @Test
    void chatByNonMemberIsNotMember() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID outsider = f.online("Bravo");
        Party party = f.partyService.create(alpha);

        assertProblem(PartyProblem.NOT_MEMBER, () -> f.partyService.chat(party.id(), outsider, "hello"));
    }

    // --- helpers --------------------------------------------------------------------------------

    /** Invites {@code name} from the leader's party (creating it if needed) and accepts; returns the party. */
    // --- Party version and preconditions --------------------------------------------------------

    @Test
    void newPartyIsAtVersionOne() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");

        assertEquals(1, f.partyService.create(alpha).version(), "a new party starts at version 1");
    }

    @Test
    void joinIncrementsVersionAndTheEventCarriesIt() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        f.online("Bravo");
        f.partyService.create(alpha);
        Invite invite = f.partyService.invite(alpha, "Bravo");

        Party joined = f.partyService.accept(invite.targetId(), invite.partyId());

        assertEquals(2, joined.version(), "a join is one change: version 1 becomes 2");
        assertEquals(2, lastEvent(f, "party.joined").version(), "the join event must carry the new version");
    }

    @Test
    void kickWithStalePreconditionChangesNothing() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        Party party = join(f, alpha, "Bravo");
        int eventsBefore = f.events.events().size();

        assertProblem(PartyProblem.PRECONDITION_FAILED,
                () -> f.partyService.kick(party.id(), alpha, bravo, new Precondition(EntityTags.of(1))));

        assertTrue(f.partyService.get(party.id()).isMember(bravo), "a stale kick must not remove the member");
        assertEquals(eventsBefore, f.events.events().size(), "a rejected kick must publish nothing");
    }

    @Test
    void kickWithCurrentPreconditionRunsAndIncrementsVersion() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        Party party = join(f, alpha, "Bravo");

        Party after = f.partyService.kick(party.id(), alpha, bravo, new Precondition(EntityTags.of(party.version())));

        assertFalse(after.isMember(bravo), "a kick whose precondition holds must remove the member");
        assertEquals(party.version() + 1, after.version(), "the kick is one change");
    }

    @Test
    void weakPreconditionNeverMatchesTheParty() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        Party party = join(f, alpha, "Bravo");

        assertProblem(PartyProblem.PRECONDITION_FAILED,
                () -> f.partyService.kick(party.id(), alpha, bravo, new Precondition("W/" + EntityTags.of(party.version()))));
    }

    @Test
    void wildcardPreconditionMatchesAnExistingParty() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        Party party = join(f, alpha, "Bravo");

        Party after = f.partyService.kick(party.id(), alpha, bravo, new Precondition("*"));

        assertFalse(after.isMember(bravo), "If-Match: * must match any existing party");
    }

    @Test
    void promoteWithStalePreconditionKeepsTheLeader() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        Party party = join(f, alpha, "Bravo");

        assertProblem(PartyProblem.PRECONDITION_FAILED,
                () -> f.partyService.promote(party.id(), alpha, bravo, new Precondition(EntityTags.of(99))));

        assertTrue(f.partyService.get(party.id()).isLeader(alpha), "a stale promote must keep the leader");
    }

    @Test
    void warpWithStalePreconditionPublishesNothing() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        Party party = f.partyService.create(alpha);
        int eventsBefore = f.events.events().size();

        assertProblem(PartyProblem.PRECONDITION_FAILED,
                () -> f.partyService.warp(party.id(), alpha, "Lobby-1", new Precondition(EntityTags.of(7))));

        assertEquals(eventsBefore, f.events.events().size(), "a rejected warp must not reach the proxies");
    }

    @Test
    void disbandWithStalePreconditionKeepsTheParty() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        Party party = f.partyService.create(alpha);

        assertProblem(PartyProblem.PRECONDITION_FAILED,
                () -> f.partyService.disband(party.id(), alpha, new Precondition(EntityTags.of(5))));

        assertEquals(party.id(), f.partyService.get(party.id()).id(), "a stale disband must keep the party");
    }

    private static Party join(PartyFixture f, UUID leader, String name) {
        Invite invite = f.partyService.invite(leader, name);
        return f.partyService.accept(invite.targetId(), invite.partyId());
    }

    private static Callable<String> attempt(PartyFixture f, CountDownLatch start, UUID player, UUID partyId) {
        return () -> {
            start.await();
            try {
                f.partyService.accept(player, partyId);
                return "ok";
            } catch (PartyException e) {
                return e.problem().code();
            }
        };
    }

    private static PartyEvent lastEvent(PartyFixture f, String type) {
        List<PartyEvent> matching = f.events.events().stream().filter(e -> e.type().equals(type)).toList();
        assertFalse(matching.isEmpty(), "no event of type " + type + " was published: " + f.events.types());
        return matching.get(matching.size() - 1);
    }

    private static String lastEventType(PartyFixture f) {
        List<String> types = f.events.types();
        return types.get(types.size() - 1);
    }

    private static void assertProblem(PartyProblem expected, org.junit.jupiter.api.function.Executable action) {
        PartyException thrown = assertThrows(PartyException.class, action, "expected " + expected.code());
        assertEquals(expected, thrown.problem(), "wrong problem for the rule that was broken");
    }
}
