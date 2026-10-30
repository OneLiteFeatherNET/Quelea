package net.onelitefeather.quelea.velocity.event;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.onelitefeather.quelea.velocity.follow.FollowMode;
import net.onelitefeather.quelea.velocity.follow.FollowPolicy;
import net.onelitefeather.quelea.velocity.i18n.Messages;
import net.onelitefeather.quelea.velocity.player.FakeLocalPlayer;
import net.onelitefeather.quelea.velocity.player.FakeLocalPlayers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The join-request prompt and answers, and the reopen notice of a leadership change that closed a public party. */
class JoinRequestEventsTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

    @TempDir
    Path overrides;

    private Messages messages;
    private FakeLocalPlayer alice;
    private FakeLocalPlayer bob;

    @BeforeEach
    void setUp() {
        messages = Messages.load(overrides, LoggerFactory.getLogger(JoinRequestEventsTest.class));
        alice = new FakeLocalPlayer(ALICE, "Alice", Locale.ENGLISH, "Lobby-1");
        bob = new FakeLocalPlayer(BOB, "Bob", Locale.ENGLISH, "Lobby-1");
    }

    private EventHandler handler(FakeLocalPlayers players) {
        return new EventHandler(players, messages,
                FollowPolicy.of("(?i)^lobby.*", FollowMode.LOBBY), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private FakeLocalPlayers both() {
        return new FakeLocalPlayers().with(alice, bob);
    }

    private static String member(UUID id, String name) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"joinedAt\":\"2026-10-28T10:00:00Z\"}";
    }

    private static String members() {
        return member(ALICE, "Alice") + "," + member(BOB, "Bob");
    }

    private static PartyEvent event(String type, String leaderId, String members, String payload) {
        return PartyEvent.parse("""
                {"id":"e-1","type":"%s","partyId":"p-1","occurredAt":"2026-10-28T10:00:00Z",
                 "leaderId":%s,"members":[%s],"payload":%s,"traceparent":null}
                """.formatted(type, leaderId == null ? "null" : "\"" + leaderId + "\"", members, payload));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static List<String> plainLines(FakeLocalPlayer player) {
        return player.sent().stream().map(JoinRequestEventsTest::plain).toList();
    }

    /** The commands run by the click actions anywhere in the component tree. */
    private static List<String> runCommands(Component component) {
        List<String> found = new ArrayList<>();
        ClickEvent click = component.clickEvent();
        if (click != null && click.payload() instanceof ClickEvent.Payload.Text text) {
            found.add(text.value());
        }
        for (Component child : component.children()) {
            found.addAll(runCommands(child));
        }
        return found;
    }

    @Test
    void theLeaderPromptOffersClickableAcceptAndDenyForTheRequester() {
        PartyEvent requested = event("party.join-requested", ALICE.toString(), members(), """
                {"requesterId":"%s","requesterName":"Bob","expiresAt":"2026-10-28T10:01:00Z"}""".formatted(BOB));

        handler(both()).handle(requested);

        assertEquals(1, alice.sent().size(), "the local leader gets one prompt");
        List<String> commands = runCommands(alice.sent().get(0));
        assertTrue(commands.contains("/party approve Bob"), "accept runs the approve command: " + commands);
        assertTrue(commands.contains("/party reject Bob"), "deny runs the reject command: " + commands);
    }

    @Test
    void thePromptIsNotSentWhenTheLeaderIsOnAnotherProxy() {
        PartyEvent requested = event("party.join-requested", ALICE.toString(), members(), """
                {"requesterId":"%s","requesterName":"Bob","expiresAt":"2026-10-28T10:01:00Z"}""".formatted(BOB));

        handler(new FakeLocalPlayers().with(bob)).handle(requested);

        assertTrue(bob.sent().isEmpty(), "a prompt is only for a local leader");
    }

    @Test
    void anAcceptedRequestTellsTheRequesterExactlyOnce() {
        PartyEvent joined = event("party.joined", ALICE.toString(), members(),
                "{\"playerId\":\"%s\",\"playerName\":\"Bob\"}".formatted(BOB));
        PartyEvent answered = event("party.join-request-answered", ALICE.toString(), members(),
                "{\"requesterId\":\"%s\",\"requesterName\":\"Bob\",\"outcome\":\"accepted\"}".formatted(BOB));

        EventHandler handler = handler(both());
        handler.handle(joined);
        handler.handle(answered);

        assertEquals(1, bob.sent().size(), "the requester gets one line, not the join line and the answer");
        assertTrue(plain(bob.sent().get(0)).contains("let you in"), plain(bob.sent().get(0)));
    }

    @Test
    void aDeniedRequestTellsTheRequester() {
        PartyEvent answered = event("party.join-request-answered", ALICE.toString(), members(),
                "{\"requesterId\":\"%s\",\"requesterName\":\"Bob\",\"outcome\":\"denied\"}".formatted(BOB));

        handler(both()).handle(answered);

        assertEquals(1, bob.sent().size());
        assertTrue(plain(bob.sent().get(0)).contains("declined your request"), plain(bob.sent().get(0)));
    }

    @Test
    void theNewLeaderWithThePermissionIsToldHowToReopenThePartyAfterItWasClosed() {
        FakeLocalPlayer newLeader = new FakeLocalPlayer(BOB, "Bob", Locale.ENGLISH, "Lobby-1")
                .withPermission("quelea.party.public");
        PartyEvent changed = event("party.leader-changed", BOB.toString(), members(),
                "{\"previousLeaderId\":\"%s\",\"leaderId\":\"%s\",\"wasPublic\":true}".formatted(ALICE, BOB));

        handler(new FakeLocalPlayers().with(newLeader)).handle(changed);

        assertTrue(plainLines(newLeader).stream().anyMatch(line -> line.contains("/party public on")),
                "the new leader must be told how to reopen: " + plainLines(newLeader));
    }

    @Test
    void theNewLeaderWithoutThePermissionIsOnlyToldThePartyIsPrivate() {
        PartyEvent changed = event("party.leader-changed", BOB.toString(), members(),
                "{\"previousLeaderId\":\"%s\",\"leaderId\":\"%s\",\"wasPublic\":true}".formatted(ALICE, BOB));

        handler(new FakeLocalPlayers().with(bob)).handle(changed);

        List<String> lines = plainLines(bob);
        assertTrue(lines.stream().anyMatch(line -> line.contains("private again")), lines.toString());
        assertFalse(lines.stream().anyMatch(line -> line.contains("/party public on")),
                "without the permission there is no reopen hint: " + lines);
    }

    @Test
    void aLeadershipChangeThatDidNotCloseAPublicPartySendsNoNotice() {
        PartyEvent changed = event("party.leader-changed", BOB.toString(), members(),
                "{\"previousLeaderId\":\"%s\",\"leaderId\":\"%s\"}".formatted(ALICE, BOB));

        handler(new FakeLocalPlayers().with(bob)).handle(changed);

        assertFalse(plainLines(bob).stream().anyMatch(line -> line.contains("private again")),
                "the closed notice is only for a change that cleared the flag");
    }
}
