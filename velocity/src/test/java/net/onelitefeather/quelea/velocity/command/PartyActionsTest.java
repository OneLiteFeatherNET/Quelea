package net.onelitefeather.quelea.velocity.command;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.onelitefeather.quelea.velocity.error.FailureReports;
import net.onelitefeather.quelea.velocity.error.FakeErrorReporter;
import net.onelitefeather.quelea.velocity.http.Invite;
import net.onelitefeather.quelea.velocity.http.Member;
import net.onelitefeather.quelea.velocity.http.Party;
import net.onelitefeather.quelea.velocity.http.QueleaProblemException;
import net.onelitefeather.quelea.velocity.http.QueleaUnavailableException;
import net.onelitefeather.quelea.velocity.i18n.Messages;
import net.onelitefeather.quelea.velocity.player.FakeLocalPlayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyActionsTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private static final String PARTY_ID = "p-1";

    private static final String PROXY_ID = "proxy-1";

    @TempDir
    Path overrides;

    private final FakeErrorReporter reporter = new FakeErrorReporter();

    private PartyActions actions(FakeQueleaGateway gateway) {
        Messages messages = Messages.load(overrides, LoggerFactory.getLogger(PartyActionsTest.class));
        FailureReports failures = new FailureReports(reporter, messages, PROXY_ID);
        // Runs the work on the calling thread, so the answer is there when the call returns.
        return new PartyActions(gateway, Runnable::run, messages, failures,
                LoggerFactory.getLogger(PartyActionsTest.class), java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));
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

    @Test
    void unexpectedExceptionIsReportedAndThePlayerGetsTheGenericAnswer() {
        FakeQueleaGateway gateway = new FakeQueleaGateway().failingWith(new IllegalStateException("boom"));
        FakeLocalPlayer bob = new FakeLocalPlayer(BOB, "Bob", Locale.ENGLISH, "Lobby-1");

        actions(gateway).invite(bob, "Alice");

        assertEquals(1, reporter.reports().size(), "one report");
        assertTrue(reporter.reports().get(0).error() instanceof IllegalStateException, "the exception is reported");
        assertTrue(lastAnswer(bob).contains("The party service is unavailable"), lastAnswer(bob));
    }

    @Test
    void serverErrorAnswerIsReported() {
        FakeQueleaGateway gateway = new FakeQueleaGateway()
                .failingWith(new QueleaProblemException("internal-error", 500, null));
        FakeLocalPlayer bob = new FakeLocalPlayer(BOB, "Bob", Locale.ENGLISH, "Lobby-1");

        actions(gateway).invite(bob, "Alice");

        assertEquals(1, reporter.reports().size(), "a 500 is reported");
        assertTrue(lastAnswer(bob).contains("An unexpected error occurred."), lastAnswer(bob));
    }

    @Test
    void documentedDomainProblemIsNotReported() {
        FakeQueleaGateway gateway = new FakeQueleaGateway()
                .failingWith(new QueleaProblemException("party-full", 409, null));
        FakeLocalPlayer bob = new FakeLocalPlayer(BOB, "Bob", Locale.ENGLISH, "Lobby-1");

        actions(gateway).invite(bob, "Alice");

        assertTrue(reporter.reports().isEmpty(), "party-full is a documented answer, not an error");
        assertTrue(lastAnswer(bob).contains("The party is full."), lastAnswer(bob));
    }

    @Test
    void credentialsStillRejectedAfterTheRefreshAreReported() {
        FakeQueleaGateway gateway = new FakeQueleaGateway()
                .failingWith(new QueleaProblemException("unauthorized", 401, null));
        FakeLocalPlayer bob = new FakeLocalPlayer(BOB, "Bob", Locale.ENGLISH, "Lobby-1");

        actions(gateway).invite(bob, "Alice");

        assertEquals(1, reporter.reports().size(), "a 401 that survived the refresh is reported");
    }

    @Test
    void networkFailureWithoutAnAnswerIsNotReported() {
        FakeQueleaGateway gateway = new FakeQueleaGateway()
                .failingWith(new QueleaUnavailableException("party service unreachable"));
        FakeLocalPlayer bob = new FakeLocalPlayer(BOB, "Bob", Locale.ENGLISH, "Lobby-1");

        actions(gateway).invite(bob, "Alice");

        assertTrue(reporter.reports().isEmpty(), "a connection failure is not reported");
    }

    @Test
    void reportTagsCarryNoPlayerNameOrUuid() {
        FakeQueleaGateway gateway = new FakeQueleaGateway().failingWith(new IllegalStateException("boom"));
        FakeLocalPlayer bob = new FakeLocalPlayer(BOB, "Bob", Locale.ENGLISH, "Lobby-1");

        actions(gateway).invite(bob, "Alice");

        Map<String, String> tags = reporter.reports().get(0).tags();
        assertEquals(Set.of("component", "proxy_id"), tags.keySet(), "only the component and the proxy are tagged");
        assertEquals("velocity", tags.get("component"));
        assertEquals(PROXY_ID, tags.get("proxy_id"));
        for (String value : tags.values()) {
            assertFalse(value.contains("Bob") || value.contains("Alice") || value.contains(BOB.toString()),
                    "no player data in tag " + value);
        }
    }
}
