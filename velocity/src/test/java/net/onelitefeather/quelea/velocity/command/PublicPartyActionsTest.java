package net.onelitefeather.quelea.velocity.command;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.onelitefeather.quelea.velocity.error.FailureReports;
import net.onelitefeather.quelea.velocity.error.FakeErrorReporter;
import net.onelitefeather.quelea.velocity.http.JoinResult;
import net.onelitefeather.quelea.velocity.http.Member;
import net.onelitefeather.quelea.velocity.http.Party;
import net.onelitefeather.quelea.velocity.http.QueleaProblemException;
import net.onelitefeather.quelea.velocity.i18n.Messages;
import net.onelitefeather.quelea.velocity.player.FakeLocalPlayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The public-party and join-request commands: the permission check, the direct join, and the leader's answers. */
class PublicPartyActionsTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private static final String PARTY_ID = "p-1";

    @TempDir
    Path overrides;

    private final FakeErrorReporter reporter = new FakeErrorReporter();

    private PartyActions actions(FakeQueleaGateway gateway) {
        Messages messages = Messages.load(overrides, LoggerFactory.getLogger(PublicPartyActionsTest.class));
        FailureReports failures = new FailureReports(reporter, messages, "proxy-1");
        return new PartyActions(gateway, Runnable::run, messages, failures,
                LoggerFactory.getLogger(PublicPartyActionsTest.class), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Party party() {
        return new Party(PARTY_ID, ALICE.toString(),
                List.of(new Member(ALICE.toString(), "Alice", NOW), new Member(BOB.toString(), "Bob", NOW)), NOW);
    }

    private static FakeLocalPlayer leader() {
        return new FakeLocalPlayer(ALICE, "Alice", Locale.ENGLISH, "Lobby-1");
    }

    private static String lastLine(FakeLocalPlayer player) {
        assertFalse(player.sent().isEmpty(), "the player must be answered");
        return PlainTextComponentSerializer.plainText().serialize(player.sent().get(player.sent().size() - 1));
    }

    @Test
    void openingWithoutThePermissionAnswersAndCallsNoService() {
        FakeQueleaGateway gateway = new FakeQueleaGateway().withParty(party());
        FakeLocalPlayer player = leader();

        actions(gateway).publicOn(player);

        assertTrue(gateway.publicCalls.isEmpty(), "the service must not be asked to open the party");
        assertTrue(lastLine(player).contains("not allowed"), lastLine(player));
    }

    @Test
    void closingWithoutThePermissionStillReachesTheService() {
        FakeQueleaGateway gateway = new FakeQueleaGateway().withParty(party());

        actions(gateway).publicOff(leader());

        assertEquals(List.of("off"), gateway.publicCalls, "closing needs no permission");
    }

    @Test
    void openingWithThePermissionAsksTheServiceAndReadsItIsOpen() {
        FakeQueleaGateway gateway = new FakeQueleaGateway().withParty(party());
        FakeLocalPlayer player = leader().withPermission("quelea.party.public");

        actions(gateway).publicOn(player);

        assertEquals(List.of("on"), gateway.publicCalls);
        assertTrue(lastLine(player).contains("open to everyone"), lastLine(player));
    }

    @Test
    void requestAnsweredJoinedShowsTheJoinNotARequest() {
        FakeQueleaGateway gateway = new FakeQueleaGateway().withRequestResult(new JoinResult.Joined(party()));
        FakeLocalPlayer player = new FakeLocalPlayer(BOB, "Bob", Locale.ENGLISH, "Lobby-1");

        actions(gateway).request(player, "Alice");

        assertTrue(lastLine(player).contains("joined"), lastLine(player));
        assertFalse(lastLine(player).contains("sent"), "a direct join must not read as a request that was sent");
    }

    @Test
    void requestAnsweredSentShowsHowLongItStaysOpen() {
        FakeQueleaGateway gateway = new FakeQueleaGateway()
                .withRequestResult(new JoinResult.Sent(NOW.plusSeconds(60)));
        FakeLocalPlayer player = new FakeLocalPlayer(BOB, "Bob", Locale.ENGLISH, "Lobby-1");

        actions(gateway).request(player, "Alice");

        assertTrue(lastLine(player).contains("sent"), lastLine(player));
        assertTrue(lastLine(player).contains("60 seconds"), lastLine(player));
    }

    @Test
    void joinOnAPrivatePartyNamesTheRequestCommand() {
        FakeQueleaGateway gateway = new FakeQueleaGateway()
                .failingWith(new QueleaProblemException("party-not-public", 409, null));
        FakeLocalPlayer player = new FakeLocalPlayer(BOB, "Bob", Locale.ENGLISH, "Lobby-1");

        actions(gateway).join(player, "Alice");

        assertTrue(lastLine(player).contains("/party request"), lastLine(player));
    }

    @Test
    void approveAndRejectAnswerTheRequesterByName() {
        FakeQueleaGateway gateway = new FakeQueleaGateway().withParty(party());
        PartyActions actions = actions(gateway);

        actions.approve(leader(), "Bob");
        actions.reject(leader(), "Bob");

        assertEquals(List.of("accept:Bob", "reject:Bob"), gateway.answers);
    }
}
