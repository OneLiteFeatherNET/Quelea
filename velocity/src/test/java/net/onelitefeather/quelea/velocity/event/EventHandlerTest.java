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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventHandlerTest {

    private static final UUID LEADER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID FOLLOWER_IN_LOBBY = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID FOLLOWER_IN_GAME = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID OFFLINE_MEMBER = UUID.fromString("00000000-0000-0000-0000-00000000000d");

    private final Messages messages = Messages.load();
    private final FollowPolicy lobbyMode = FollowPolicy.of("(?i)^lobby.*", FollowMode.LOBBY);

    private FakeLocalPlayer leader;
    private FakeLocalPlayer lobbyFollower;
    private FakeLocalPlayer gameFollower;
    private FakeLocalPlayers players;

    @BeforeEach
    void setUp() {
        leader = new FakeLocalPlayer(LEADER, "Alice", Locale.ENGLISH, "Lobby-1");
        lobbyFollower = new FakeLocalPlayer(FOLLOWER_IN_LOBBY, "Bob", Locale.ENGLISH, "Lobby-1");
        gameFollower = new FakeLocalPlayer(FOLLOWER_IN_GAME, "Carol", Locale.ENGLISH, "Tamias-1");
        players = new FakeLocalPlayers().with(leader, lobbyFollower, gameFollower);
    }

    private EventHandler handler() {
        return new EventHandler(players, messages, lobbyMode);
    }

    private static String member(UUID id, String name) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"joinedAt\":\"2026-10-28T10:00:00Z\"}";
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
    void inviteMessageOffersClickableAcceptAndDeny() {
        PartyEvent invited = event("party.invited", LEADER.toString(), "", """
                {"inviterId":"%s","inviterName":"Alice","targetId":"%s","targetName":"Bob",
                 "expiresAt":"2026-10-28T10:01:00Z"}""".formatted(LEADER, FOLLOWER_IN_LOBBY));

        handler().handle(invited);

        assertEquals(1, lobbyFollower.sent().size(), "the target gets one message");
        Component message = lobbyFollower.sent().get(0);
        assertTrue(plain(message).contains("Alice"), plain(message));
        List<String> commands = runCommands(message);
        assertTrue(commands.contains("/party accept Alice"), "accept is clickable: " + commands);
        assertTrue(commands.contains("/party deny Alice"), "deny is clickable: " + commands);
    }

    @Test
    void warpMovesEligibleLocalMembersOnly() {
        PartyEvent warp = event("party.warp", LEADER.toString(), """
                %s,%s,%s,%s""".formatted(
                member(LEADER, "Alice"), member(FOLLOWER_IN_LOBBY, "Bob"),
                member(FOLLOWER_IN_GAME, "Carol"), member(OFFLINE_MEMBER, "Dave")), """
                {"server":"Bounce-2"}""");

        handler().handle(warp);

        assertEquals(List.of("Bounce-2"), lobbyFollower.connectRequests(), "the lobby member follows");
        assertTrue(gameFollower.connectRequests().isEmpty(), "a member in another game stays where they are");
        assertTrue(leader.connectRequests().isEmpty(), "the leader is never moved by the warp");
    }

    @Test
    void refusedWarpTellsTheMemberWhy() {
        lobbyFollower.refusingConnections();
        PartyEvent warp = event("party.warp", LEADER.toString(), """
                %s,%s""".formatted(member(LEADER, "Alice"), member(FOLLOWER_IN_LOBBY, "Bob")), """
                {"server":"Bounce-2"}""");

        handler().handle(warp);

        assertEquals(1, lobbyFollower.sent().size(), "the refused member is told");
        assertTrue(plain(lobbyFollower.sent().get(0)).contains("Bounce-2"), "the message names the server");
    }

    @Test
    void disbandedNotifiesEveryFormerMember() {
        PartyEvent disbanded = event("party.disbanded", LEADER.toString(), "", """
                {"formerMembers":[%s,%s],"reason":"leader-disbanded"}""".formatted(
                "{\"id\":\"" + LEADER + "\",\"name\":\"Alice\"}",
                "{\"id\":\"" + FOLLOWER_IN_LOBBY + "\",\"name\":\"Bob\"}"));

        handler().handle(disbanded);

        assertEquals(1, leader.sent().size(), "the former leader is told");
        assertEquals(1, lobbyFollower.sent().size(), "the former member is told");
        assertTrue(gameFollower.sent().isEmpty(), "players who were not in the party are not told");
    }

    @Test
    void joinedNotifiesMembersAndGivesTheNewMemberTheirOwnMessage() {
        PartyEvent joined = event("party.joined", LEADER.toString(), """
                %s,%s""".formatted(member(LEADER, "Alice"), member(FOLLOWER_IN_LOBBY, "Bob")), """
                {"playerId":"%s","playerName":"Bob"}""".formatted(FOLLOWER_IN_LOBBY));

        handler().handle(joined);

        assertEquals(1, lobbyFollower.sent().size(), "the joining player gets exactly one message");
        assertTrue(plain(lobbyFollower.sent().get(0)).contains("You joined"), plain(lobbyFollower.sent().get(0)));
        assertEquals(1, leader.sent().size(), "the leader is told about the new member");
        assertTrue(plain(leader.sent().get(0)).contains("Bob"), plain(leader.sent().get(0)));
    }

    @Test
    void chatReachesEveryLocalMemberInTheirOwnLanguage() {
        FakeLocalPlayer german = new FakeLocalPlayer(FOLLOWER_IN_GAME, "Carol", Locale.GERMAN, "Tamias-1");
        players = new FakeLocalPlayers().with(leader, german);
        PartyEvent chat = event("party.chat", LEADER.toString(), """
                %s,%s""".formatted(member(LEADER, "Alice"), member(FOLLOWER_IN_GAME, "Carol")), """
                {"senderId":"%s","senderName":"Alice","message":"gg"}""".formatted(LEADER));

        handler().handle(chat);

        assertEquals(1, leader.sent().size());
        assertEquals("[Party] Alice: gg", plain(leader.sent().get(0)));
        assertEquals("[Party] Alice: gg", plain(german.sent().get(0)), "chat lines carry no translated text");
    }

    @Test
    void unknownEventTypesAreIgnored() {
        PartyEvent future = event("party.teleported-to-moon", LEADER.toString(), """
                %s""".formatted(member(LEADER, "Alice")), "{}");

        handler().handle(future);

        assertFalse(leader.sent().iterator().hasNext(), "nothing is sent for an unknown event type");
    }
}
