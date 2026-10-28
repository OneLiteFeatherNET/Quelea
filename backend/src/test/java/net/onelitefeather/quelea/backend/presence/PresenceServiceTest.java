package net.onelitefeather.quelea.backend.presence;

import net.onelitefeather.quelea.backend.party.Invite;
import net.onelitefeather.quelea.backend.party.Party;
import net.onelitefeather.quelea.backend.problem.PartyException;
import net.onelitefeather.quelea.backend.problem.PartyProblem;
import net.onelitefeather.quelea.backend.support.PartyFixture;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresenceServiceTest {

    private static final String OTHER_PROXY = "proxy-2";

    // --- Presence updates -----------------------------------------------------------------------

    @Test
    void loginRecordsPresenceOnProxy() {
        PartyFixture f = new PartyFixture();
        UUID alpha = UUID.randomUUID();

        f.presenceService.login(PartyFixture.PROXY, alpha, "Alpha");

        PlayerPresence presence = f.presence.find(alpha).orElseThrow();
        assertEquals(PartyFixture.PROXY, presence.proxyId(), "the login must place the player on the reporting proxy");
        assertEquals("Alpha", presence.name(), "the login must record the name");
        assertTrue(f.presence.isAlive(PartyFixture.PROXY), "a login must also count as a heartbeat");
    }

    @Test
    void disconnectOfMemberLeavesPartyAndPublishesLeft() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        UUID charlie = f.online("Charlie");
        Party party = f.partyService.create(alpha);
        accept(f, alpha, "Bravo", party);
        accept(f, alpha, "Charlie", party);

        f.presenceService.disconnect(PartyFixture.PROXY, bravo);

        assertFalse(f.presence.find(bravo).isPresent(), "the disconnected player must be offline");
        assertFalse(f.partyService.get(party.id()).isMember(bravo), "the disconnected player must have left the party");
        assertTrue(f.partyService.get(party.id()).isMember(charlie), "other members must stay");
        assertTrue(f.events.types().contains("party.left"), "the departure must be published as party.left");
        assertEquals("disconnect", f.events.events().stream()
                .filter(e -> e.type().equals("party.left")).reduce((a, b) -> b).orElseThrow()
                .payload().get("reason"), "the reason must be disconnect");
    }

    @Test
    void disconnectDropsPendingInvites() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        f.partyService.invite(alpha, "Bravo");

        f.presenceService.disconnect(PartyFixture.PROXY, bravo);

        assertTrue(f.partyService.invitesOf(bravo).isEmpty(), "the disconnect must drop the player's invites");
    }

    @Test
    void disconnectFromOtherProxyIsIgnored() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        Party party = f.partyService.create(alpha);
        accept(f, alpha, "Bravo", party);

        f.presenceService.disconnect(OTHER_PROXY, bravo);

        assertTrue(f.presence.find(bravo).isPresent(), "a disconnect reported by another proxy must not remove the player");
        assertTrue(f.partyService.get(party.id()).isMember(bravo), "the player must stay in the party");
    }

    @Test
    void disconnectOfPlayerOutsideAnyPartyIsNoError() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");

        f.presenceService.disconnect(PartyFixture.PROXY, alpha);

        assertFalse(f.presence.find(alpha).isPresent(), "the player must still be removed from presence");
    }

    // --- Snapshot -------------------------------------------------------------------------------

    @Test
    void snapshotDisconnectsPlayersMissingFromIt() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        f.online("Charlie");
        Party party = f.partyService.create(alpha);
        accept(f, alpha, "Bravo", party);
        accept(f, alpha, "Charlie", party);

        UUID charlie = f.presence.findByName("Charlie").orElseThrow().id();

        f.presenceService.snapshot(PartyFixture.PROXY, List.of(new PlayerRef(alpha, "Alpha"), new PlayerRef(charlie, "Charlie")));

        assertEquals(Set.of(alpha, charlie), f.presence.playersOf(PartyFixture.PROXY), "the proxy's player set must equal the snapshot");
        assertFalse(f.presence.find(bravo).isPresent(), "a player missing from the snapshot must be treated as disconnected");
        assertFalse(f.partyService.get(party.id()).isMember(bravo), "and must have left the party");
    }

    @Test
    void snapshotAddsPlayersUnknownToTheService() {
        PartyFixture f = new PartyFixture();
        UUID newcomer = UUID.randomUUID();

        f.presenceService.snapshot(PartyFixture.PROXY, List.of(new PlayerRef(newcomer, "Newcomer")));

        assertEquals("Newcomer", f.presence.find(newcomer).orElseThrow().name(), "a listed player must become present");
    }

    @Test
    void snapshotDoesNotTouchPlayersOfOtherProxies() {
        PartyFixture f = new PartyFixture();
        UUID elsewhere = UUID.randomUUID();
        f.presence.put(new PlayerPresence(elsewhere, "Elsewhere", OTHER_PROXY, f.clock.instant()));

        f.presenceService.snapshot(PartyFixture.PROXY, List.of());

        assertTrue(f.presence.find(elsewhere).isPresent(), "a snapshot of one proxy must not disconnect players of another");
    }

    // --- Heartbeats and dead proxies ------------------------------------------------------------

    @Test
    void deadProxyPlayersAreOfflineAndLeftTheirParty() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        UUID bravo = f.online("Bravo");
        Party party = f.partyService.create(alpha);
        accept(f, alpha, "Bravo", party);

        f.clock.advance(Duration.ofSeconds(46));
        f.presenceService.reap();

        assertFalse(f.presence.find(alpha).isPresent(), "a player of a dead proxy must be offline");
        assertFalse(f.presence.find(bravo).isPresent(), "a player of a dead proxy must be offline");
        assertFalse(f.presence.proxies().contains(PartyFixture.PROXY), "the dead proxy must be forgotten");
        assertThrowsPartyNotFound(f, party.id());
    }

    @Test
    void heartbeatKeepsProxyAndItsPlayersAlive() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");

        f.clock.advance(Duration.ofSeconds(40));
        f.presenceService.heartbeat(PartyFixture.PROXY);
        f.clock.advance(Duration.ofSeconds(40));
        f.presenceService.reap();

        assertTrue(f.presence.find(alpha).isPresent(), "a proxy with a recent heartbeat must not be reaped");
        assertTrue(f.presence.proxies().contains(PartyFixture.PROXY), "a live proxy must stay known");
    }

    @Test
    void reapWithoutDeadProxiesChangesNothing() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");

        f.presenceService.reap();

        assertTrue(f.presence.find(alpha).isPresent(), "a sweep with only live proxies must not disconnect anyone");
    }

    // --- helpers --------------------------------------------------------------------------------

    private static void accept(PartyFixture f, UUID leader, String name, Party party) {
        Invite invite = f.partyService.invite(leader, name);
        f.partyService.accept(invite.targetId(), party.id());
    }

    private static void assertThrowsPartyNotFound(PartyFixture f, UUID partyId) {
        try {
            f.partyService.get(partyId);
        } catch (PartyException e) {
            assertEquals(PartyProblem.PARTY_NOT_FOUND, e.problem(), "the party must be gone");
            return;
        }
        throw new AssertionError("the party must no longer exist");
    }
}
