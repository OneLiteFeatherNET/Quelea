package net.onelitefeather.quelea.backend.store.redis;

import io.micronaut.serde.ObjectMapper;
import net.onelitefeather.quelea.backend.party.Invite;
import net.onelitefeather.quelea.backend.party.Member;
import net.onelitefeather.quelea.backend.party.Party;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
class RedisPartyStoreTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private static DragonflyFixture dragonfly;

    private RedisPartyStore store;

    @BeforeAll
    static void startDragonfly() {
        dragonfly = DragonflyFixture.start();
    }

    @AfterAll
    static void stopDragonfly() {
        if (dragonfly != null) {
            dragonfly.close();
        }
    }

    @BeforeEach
    void freshStore() {
        dragonfly.flushAll();
        store = new RedisPartyStore(dragonfly.connection(), new RedisKeys("quelea:"), ObjectMapper.getDefault());
    }

    @Test
    void claimPlayerBindsFreePlayer() {
        UUID player = UUID.randomUUID();
        UUID party = UUID.randomUUID();

        assertTrue(store.claimPlayer(player, party));
        assertEquals(party, store.partyOf(player).orElseThrow());
    }

    @Test
    void claimPlayerRefusesPlayerThatIsAlreadyBound() {
        UUID player = UUID.randomUUID();
        UUID first = UUID.randomUUID();

        store.claimPlayer(player, first);

        assertFalse(store.claimPlayer(player, UUID.randomUUID()));
        assertEquals(first, store.partyOf(player).orElseThrow());
    }

    @Test
    void releasePlayerWithMatchingPartyIdRemovesBinding() {
        UUID player = UUID.randomUUID();
        UUID party = UUID.randomUUID();
        store.claimPlayer(player, party);

        store.releasePlayer(player, party);

        assertTrue(store.partyOf(player).isEmpty());
    }

    @Test
    void releasePlayerWithOtherPartyIdKeepsBinding() {
        UUID player = UUID.randomUUID();
        UUID party = UUID.randomUUID();
        store.claimPlayer(player, party);

        store.releasePlayer(player, UUID.randomUUID());

        assertEquals(party, store.partyOf(player).orElseThrow());
    }

    @Test
    void saveThenFindReturnsTheSameParty() {
        Party party = party(UUID.randomUUID());

        store.save(party);

        assertEquals(party, store.find(party.id()).orElseThrow());
    }

    @Test
    void partyWithAloneSinceRoundTripsIt() {
        Party alone = new Party(UUID.randomUUID(), UUID.randomUUID(), List.of(new Member(UUID.randomUUID(), "Alpha", NOW)),
                NOW, 3, NOW.plus(Duration.ofMinutes(10)));

        store.save(alone);

        assertEquals(NOW.plus(Duration.ofMinutes(10)), store.find(alone.id()).orElseThrow().aloneSince(),
                "the stored alone-since time must come back unchanged");
    }

    @Test
    void partyStoredWithoutAloneSinceReadsItAsNullAndStartsAtCreation() {
        Party legacy = party(UUID.randomUUID());

        store.save(legacy);
        Party read = store.find(legacy.id()).orElseThrow();

        assertEquals(null, read.aloneSince(), "a party without a stored alone-since time must read it as null");
        assertEquals(NOW, read.aloneStart(), "without alone-since, the alone period starts at creation");
    }

    @Test
    void restartedAlonePeriodWithoutWarningDropsTheOldWarning() {
        UUID party = UUID.randomUUID();
        store.markAlone(party, NOW.plus(Duration.ofMinutes(30)), NOW.plus(Duration.ofMinutes(25)));

        store.markAlone(party, NOW.plus(Duration.ofMinutes(60)), null);

        assertTrue(store.dueWarnings(NOW.plus(Duration.ofMinutes(26))).isEmpty(),
                "the warning of the earlier alone period must not fire after the restart without warning");
        assertEquals(List.of(party), store.dueExpiries(NOW.plus(Duration.ofMinutes(60))), "the new expiry must be scheduled");
    }

    @Test
    void findReturnsEmptyForUnknownParty() {
        assertTrue(store.find(UUID.randomUUID()).isEmpty());
    }

    @Test
    void deleteRemovesPartyAndCountsOnlyRemainingParties() {
        Party kept = party(UUID.randomUUID());
        Party gone = party(UUID.randomUUID());
        store.save(kept);
        store.save(gone);

        store.delete(gone.id());

        assertTrue(store.find(gone.id()).isEmpty());
        assertEquals(1, store.countParties());
    }

    @Test
    void countPartiesIsZeroWhenNoPartyWasSaved() {
        assertEquals(0, store.countParties());
    }

    @Test
    void saveThenFindInviteReturnsInvite() {
        Invite invite = invite(UUID.randomUUID(), UUID.randomUUID());

        store.saveInvite(invite, Duration.ofSeconds(60));

        assertEquals(invite, store.findInvite(invite.partyId(), invite.targetId()).orElseThrow());
    }

    @Test
    void inviteIsGoneAfterItsTtl() {
        Invite invite = invite(UUID.randomUUID(), UUID.randomUUID());
        store.saveInvite(invite, Duration.ofMillis(500));

        assertTrue(store.findInvite(invite.partyId(), invite.targetId()).isPresent());
        assertTrue(awaitGone(() -> store.findInvite(invite.partyId(), invite.targetId()).isEmpty()),
                "invite should expire with its TTL");
    }

    @Test
    void invitesOfSkipsExpiredInvitesAndKeepsLiveOnes() {
        UUID target = UUID.randomUUID();
        Invite shortLived = invite(UUID.randomUUID(), target);
        Invite live = invite(UUID.randomUUID(), target);
        store.saveInvite(shortLived, Duration.ofMillis(500));
        store.saveInvite(live, Duration.ofSeconds(60));

        assertTrue(awaitGone(() -> store.invitesOf(target).size() == 1),
                "the expired invite should no longer be listed");
        assertEquals(List.of(live), store.invitesOf(target));
    }

    @Test
    void invitesOfIgnoresInvitesOfOtherPlayers() {
        UUID target = UUID.randomUUID();
        Invite mine = invite(UUID.randomUUID(), target);
        store.saveInvite(mine, Duration.ofSeconds(60));
        store.saveInvite(invite(UUID.randomUUID(), UUID.randomUUID()), Duration.ofSeconds(60));

        assertEquals(List.of(mine), store.invitesOf(target));
    }

    @Test
    void deleteInviteRemovesOnlyThatInvite() {
        UUID target = UUID.randomUUID();
        Invite deleted = invite(UUID.randomUUID(), target);
        Invite kept = invite(UUID.randomUUID(), target);
        store.saveInvite(deleted, Duration.ofSeconds(60));
        store.saveInvite(kept, Duration.ofSeconds(60));

        store.deleteInvite(deleted.partyId(), target);

        assertTrue(store.findInvite(deleted.partyId(), target).isEmpty());
        assertEquals(List.of(kept), store.invitesOf(target));
    }

    @Test
    void deleteInvitesOfRemovesAllInvitesOfThatPlayerOnly() {
        UUID target = UUID.randomUUID();
        UUID otherTarget = UUID.randomUUID();
        store.saveInvite(invite(UUID.randomUUID(), target), Duration.ofSeconds(60));
        store.saveInvite(invite(UUID.randomUUID(), target), Duration.ofSeconds(60));
        Invite unrelated = invite(UUID.randomUUID(), otherTarget);
        store.saveInvite(unrelated, Duration.ofSeconds(60));

        store.deleteInvitesOf(target);

        assertTrue(store.invitesOf(target).isEmpty());
        assertEquals(List.of(unrelated), store.invitesOf(otherTarget));
    }

    private static boolean awaitGone(BooleanSupplier condition) {
        return DragonflyFixture.awaitTrue(condition, Duration.ofSeconds(2));
    }

    private static Party party(UUID leader) {
        Member member = new Member(leader, "Leader", NOW);
        return new Party(UUID.randomUUID(), leader, List.of(member), NOW, 1);
    }

    private static Invite invite(UUID partyId, UUID targetId) {
        return new Invite(partyId, UUID.randomUUID(), "Inviter", targetId, "Target", NOW.plusSeconds(60));
    }

    @Test
    void aloneWarningIsDueOnlyFromItsTime() {
        UUID party = UUID.randomUUID();
        store.markAlone(party, NOW.plus(Duration.ofMinutes(30)), NOW.plus(Duration.ofMinutes(25)));

        assertTrue(store.dueWarnings(NOW.plus(Duration.ofMinutes(24))).isEmpty(), "no warning may be due before its time");
        assertEquals(List.of(party), store.dueWarnings(NOW.plus(Duration.ofMinutes(25))), "the warning must be due at its time");
    }

    @Test
    void aloneExpiryIsDueOnlyFromItsTime() {
        UUID party = UUID.randomUUID();
        store.markAlone(party, NOW.plus(Duration.ofMinutes(30)), NOW.plus(Duration.ofMinutes(25)));

        assertTrue(store.dueExpiries(NOW.plus(Duration.ofMinutes(29))).isEmpty(), "no expiry may be due before its time");
        assertEquals(List.of(party), store.dueExpiries(NOW.plus(Duration.ofMinutes(30))), "the expiry must be due at its time");
    }

    @Test
    void markWithoutWarningAddsNoWarning() {
        UUID party = UUID.randomUUID();
        store.markAlone(party, NOW.plus(Duration.ofMinutes(30)), null);

        assertTrue(store.dueWarnings(NOW.plus(Duration.ofHours(1))).isEmpty(), "a party marked without warning must never be warned");
        assertEquals(List.of(party), store.dueExpiries(NOW.plus(Duration.ofHours(1))), "the expiry must still be scheduled");
    }

    @Test
    void clearAloneRemovesBothEntries() {
        UUID party = UUID.randomUUID();
        store.markAlone(party, NOW.plus(Duration.ofMinutes(30)), NOW.plus(Duration.ofMinutes(25)));

        store.clearAlone(party);

        assertTrue(store.dueWarnings(NOW.plus(Duration.ofHours(1))).isEmpty(), "a cleared party must not be warned");
        assertTrue(store.dueExpiries(NOW.plus(Duration.ofHours(1))).isEmpty(), "a cleared party must not expire");
    }
}
