package net.onelitefeather.quelea.velocity.command;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.onelitefeather.quelea.velocity.http.Invite;
import net.onelitefeather.quelea.velocity.http.Member;
import net.onelitefeather.quelea.velocity.http.Party;
import net.onelitefeather.quelea.velocity.i18n.Messages;
import net.onelitefeather.quelea.velocity.player.FakeLocalPlayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyActionsTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private static final String PARTY_ID = "p-1";

    @TempDir
    Path overrides;

    private PartyActions actions(FakeQueleaGateway gateway) {
        Messages messages = Messages.load(overrides, LoggerFactory.getLogger(PartyActionsTest.class));
        // Runs the work on the calling thread, so the answer is there when the call returns.
        return new PartyActions(gateway, Runnable::run, messages, LoggerFactory.getLogger(PartyActionsTest.class));
    }

    private static Party party() {
        return new Party(PARTY_ID, ALICE.toString(),
                List.of(new Member(ALICE.toString(), "Alice", NOW), new Member(BOB.toString(), "Bob", NOW)), NOW);
    }

    private static Invite invite() {
        return new Invite(PARTY_ID, ALICE.toString(), "Alice", BOB.toString(), "Bob", NOW.plusSeconds(60));
    }

    private static String lastAnswer(FakeLocalPlayer player) {
        return PlainTextComponentSerializer.plainText().serialize(player.sent().get(player.sent().size() - 1));
    }

    @Test
    void acceptAgainFromAPartyThePlayerAlreadyJoinedSaysSo() {
        FakeQueleaGateway gateway = new FakeQueleaGateway().withParty(party());
        FakeLocalPlayer bob = new FakeLocalPlayer(BOB, "Bob", Locale.ENGLISH, "Lobby-1");

        actions(gateway).accept(bob, "Alice");

        assertEquals(1, bob.sent().size(), "one answer");
        assertTrue(lastAnswer(bob).contains("You are already in this party."), lastAnswer(bob));
        assertTrue(gateway.accepted.isEmpty(), "nothing is joined twice");
    }

    @Test
    void acceptWithoutAnyInviteOutsideAPartySaysNoInvitesAreOpen() {
        FakeQueleaGateway gateway = new FakeQueleaGateway();
        FakeLocalPlayer bob = new FakeLocalPlayer(BOB, "Bob", Locale.ENGLISH, "Lobby-1");

        actions(gateway).accept(bob, null);

        assertTrue(lastAnswer(bob).contains("no pending party invites"), lastAnswer(bob));
    }

    @Test
    void acceptWithAMatchingInviteJoinsAndAnswersOnce() {
        FakeQueleaGateway gateway = new FakeQueleaGateway().withInvite(invite()).withParty(party());
        FakeLocalPlayer bob = new FakeLocalPlayer(BOB, "Bob", Locale.ENGLISH, "Lobby-1");

        actions(gateway).accept(bob, null);

        assertEquals(List.of(PARTY_ID), gateway.accepted);
        assertEquals(1, bob.sent().size(), "one answer");
        assertTrue(lastAnswer(bob).contains("You joined the party."), lastAnswer(bob));
    }
}
