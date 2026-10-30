package net.onelitefeather.quelea.velocity.event;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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

class EventHandlerTest {

    private static final UUID LEADER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID FOLLOWER_IN_LOBBY = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID FOLLOWER_IN_GAME = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID OFFLINE_MEMBER = UUID.fromString("00000000-0000-0000-0000-00000000000d");
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

    @TempDir
    Path overrides;

    private Messages messages;
    private final FollowPolicy lobbyMode = FollowPolicy.of("(?i)^lobby.*", FollowMode.LOBBY);

    private FakeLocalPlayer leader;
    private FakeLocalPlayer lobbyFollower;
    private FakeLocalPlayer gameFollower;
    private FakeLocalPlayers players;

    @BeforeEach
    void setUp() {
        messages = Messages.load(overrides, LoggerFactory.getLogger(EventHandlerTest.class));
        leader = new FakeLocalPlayer(LEADER, "Alice", Locale.ENGLISH, "Lobby-1");
        lobbyFollower = new FakeLocalPlayer(FOLLOWER_IN_LOBBY, "Bob", Locale.ENGLISH, "Lobby-1");
        gameFollower = new FakeLocalPlayer(FOLLOWER_IN_GAME, "Carol", Locale.ENGLISH, "Tamias-1");
        players = new FakeLocalPlayers().with(leader, lobbyFollower, gameFollower);
    }

    private EventHandler handler() {
        return new EventHandler(players, messages, lobbyMode, Clock.fixed(NOW, ZoneOffset.UTC));
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

    private static final UUID CO_PLAYER_ON_ORIGIN = UUID.fromString("00000000-0000-0000-0000-00000000000e");

    private EventHandler handler(FollowPolicy policy) {
        return new EventHandler(players, messages, policy, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static PartyEvent warp(String target, String origin) {
        String payload = origin == null
                ? "{\"server\":\"" + target + "\"}"
                : "{\"server\":\"" + target + "\",\"from\":\"" + origin + "\"}";
        return event("party.warp", LEADER.toString(), """
                %s,%s,%s,%s""".formatted(member(LEADER, "Alice"), member(FOLLOWER_IN_LOBBY, "Bob"),
                member(FOLLOWER_IN_GAME, "Carol"), member(CO_PLAYER_ON_ORIGIN, "Dave")), payload);
    }

    @Test
    void leaderLeavingAGameWarpsTheCoPlayerOnTheOriginAndTheLobbyMember() {
        FakeLocalPlayer coPlayer = new FakeLocalPlayer(CO_PLAYER_ON_ORIGIN, "Dave", Locale.ENGLISH, "Bounce-2");
        players = new FakeLocalPlayers().with(leader, lobbyFollower, gameFollower, coPlayer);

        handler(lobbyMode).handle(warp("Lobby-2", "Bounce-2"));

        assertEquals(List.of("Lobby-2"), coPlayer.connectRequests(), "the co-player on the game the leader left follows");
        assertEquals(List.of("Lobby-2"), lobbyFollower.connectRequests(), "a member in a lobby follows");
        assertTrue(gameFollower.connectRequests().isEmpty(), "a member in another game stays where they are");
        assertTrue(leader.connectRequests().isEmpty(), "the leader is never moved by the warp");
    }

    @Test
    void leaderJoiningAGameWarpsTheLobbyMembersOnly() {
        FakeLocalPlayer coPlayer = new FakeLocalPlayer(CO_PLAYER_ON_ORIGIN, "Dave", Locale.ENGLISH, "Lobby-1");
        players = new FakeLocalPlayers().with(leader, lobbyFollower, gameFollower, coPlayer);

        handler(lobbyMode).handle(warp("Bounce-2", "Lobby-1"));

        assertEquals(List.of("Bounce-2"), lobbyFollower.connectRequests(), "the lobby member follows");
        assertEquals(List.of("Bounce-2"), coPlayer.connectRequests(), "the lobby member follows");
        assertTrue(gameFollower.connectRequests().isEmpty(), "a member in another game stays where they are");
    }

    @Test
    void leaderMovingBetweenLobbiesAlignsTheMembersInTheOtherLobbies() {
        FakeLocalPlayer otherInstance = new FakeLocalPlayer(CO_PLAYER_ON_ORIGIN, "Dave", Locale.ENGLISH, "Lobby-3");
        players = new FakeLocalPlayers().with(leader, lobbyFollower, gameFollower, otherInstance);

        handler(lobbyMode).handle(warp("Lobby-2", "Lobby-1"));

        assertEquals(List.of("Lobby-2"), lobbyFollower.connectRequests(), "a member on the leader's old lobby follows");
        assertEquals(List.of("Lobby-2"), otherInstance.connectRequests(), "a member on another lobby instance follows");
        assertTrue(gameFollower.connectRequests().isEmpty(), "a member in a game stays where they are");
    }

    @Test
    void warpWithoutOriginKeepsTheLobbyRule() {
        FakeLocalPlayer coPlayer = new FakeLocalPlayer(CO_PLAYER_ON_ORIGIN, "Dave", Locale.ENGLISH, "Bounce-2");
        players = new FakeLocalPlayers().with(leader, lobbyFollower, gameFollower, coPlayer);

        handler(lobbyMode).handle(warp("Lobby-2", null));

        assertEquals(List.of("Lobby-2"), lobbyFollower.connectRequests(), "the lobby member follows as before");
        assertTrue(coPlayer.connectRequests().isEmpty(), "without an origin a member in a game does not follow");
        assertTrue(gameFollower.connectRequests().isEmpty(), "a member in another game stays where they are");
    }

    @Test
    void alwaysModeMovesEveryLocalMemberIncludingTheOneInAnotherGame() {
        FakeLocalPlayer coPlayer = new FakeLocalPlayer(CO_PLAYER_ON_ORIGIN, "Dave", Locale.ENGLISH, "Bounce-2");
        players = new FakeLocalPlayers().with(leader, lobbyFollower, gameFollower, coPlayer);

        handler(FollowPolicy.of("(?i)^lobby.*", FollowMode.ALWAYS)).handle(warp("Lobby-2", "Bounce-2"));

        assertEquals(List.of("Lobby-2"), gameFollower.connectRequests(), "always: a member in another game follows too");
        assertEquals(List.of("Lobby-2"), coPlayer.connectRequests(), "always: the co-player follows");
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
    void disbandByTheLeaderDoesNotNotifyTheLeaderAgain() {
        PartyEvent disbanded = event("party.disbanded", LEADER.toString(), "", """
                {"formerMembers":[%s,%s],"reason":"leader"}""".formatted(
                "{\"id\":\"" + LEADER + "\",\"name\":\"Alice\"}",
                "{\"id\":\"" + FOLLOWER_IN_LOBBY + "\",\"name\":\"Bob\"}"));

        handler().handle(disbanded);

        assertTrue(leader.sent().isEmpty(), "the disbanding leader reads only the command answer");
        assertEquals(1, lobbyFollower.sent().size(), "the former member is told");
    }

    @Test
    void disbandedWithOtherReasonKeepsTheGenericMessage() {
        PartyEvent disbanded = event("party.disbanded", LEADER.toString(), "", """
                {"formerMembers":[{"id":"%s","name":"Alice"}],"reason":"last-member-left"}""".formatted(LEADER));

        handler().handle(disbanded);

        assertTrue(plain(leader.sent().get(0)).contains("The party was disbanded."), "a reason without its own text keeps the generic one");
    }

    @Test
    void disbandedExpiredAloneTellsTheFormerLeaderThatNobodyJoined() {
        PartyEvent disbanded = event("party.disbanded", LEADER.toString(), "", """
                {"formerMembers":[{"id":"%s","name":"Alice"}],"reason":"expired-alone"}""".formatted(LEADER));

        handler().handle(disbanded);

        assertTrue(plain(leader.sent().get(0)).contains("nobody joined"), "the former leader must learn why: " + plain(leader.sent().get(0)));
    }

    @Test
    void expiringTellsTheLocalLeaderTheMinutesLeftAndHowToInvite() {
        PartyEvent expiring = event("party.expiring", LEADER.toString(), member(LEADER, "Alice"), """
                {"expiresAt":"2026-10-28T10:05:00Z"}""");

        handler().handle(expiring);

        String text = plain(leader.sent().get(0));
        assertTrue(text.contains("closes in 5 minutes"), "the leader must read the minutes left: " + text);
        assertTrue(text.contains("/party invite"), "the leader must read how to invite someone: " + text);
    }

    @Test
    void expiringRoundsPartOfAMinuteUp() {
        PartyEvent expiring = event("party.expiring", LEADER.toString(), member(LEADER, "Alice"), """
                {"expiresAt":"2026-10-28T10:04:30Z"}""");

        handler().handle(expiring);

        assertTrue(plain(leader.sent().get(0)).contains("closes in 5 minutes"), "4 minutes 30 seconds must read as 5 minutes");
    }

    @Test
    void expiringIsNotSentToMembersWhoAreNotTheLeader() {
        PartyEvent expiring = event("party.expiring", LEADER.toString(), """
                %s,%s""".formatted(member(LEADER, "Alice"), member(FOLLOWER_IN_LOBBY, "Bob")), """
                {"expiresAt":"2026-10-28T10:05:00Z"}""");

        handler().handle(expiring);

        assertEquals(1, leader.sent().size(), "the leader is told");
        assertTrue(lobbyFollower.sent().isEmpty(), "a member who is not the leader is not told about the warning");
    }

    @Test
    void acceptNotifiesTheOtherMembersButNotTheJoiner() {
        PartyEvent joined = event("party.joined", LEADER.toString(), """
                %s,%s""".formatted(member(LEADER, "Alice"), member(FOLLOWER_IN_LOBBY, "Bob")), """
                {"playerId":"%s","playerName":"Bob"}""".formatted(FOLLOWER_IN_LOBBY));

        handler().handle(joined);

        assertTrue(lobbyFollower.sent().isEmpty(), "the joiner reads only the command answer");
        assertEquals(1, leader.sent().size(), "the leader is told about the new member");
        assertTrue(plain(leader.sent().get(0)).contains("Bob"), plain(leader.sent().get(0)));
    }

    @Test
    void createDoesNotNotifyTheLeaderWhoRanTheCommand() {
        PartyEvent created = event("party.created", LEADER.toString(), member(LEADER, "Alice"), "{}");

        handler().handle(created);

        assertTrue(leader.sent().isEmpty(), "the leader reads only the command answer");
    }

    @Test
    void kickNotifiesTheKickedPlayerAndTheOtherMembersButNotTheLeader() {
        PartyEvent kicked = event("party.kicked", LEADER.toString(), """
                %s,%s""".formatted(member(LEADER, "Alice"), member(FOLLOWER_IN_GAME, "Carol")), """
                {"playerId":"%s","playerName":"Bob"}""".formatted(FOLLOWER_IN_LOBBY));

        handler().handle(kicked);

        assertTrue(leader.sent().isEmpty(), "the kicking leader reads only the command answer");
        assertEquals(1, lobbyFollower.sent().size(), "the kicked player is told");
        assertEquals(1, gameFollower.sent().size(), "the other member is told");
    }

    @Test
    void leaveNotifiesTheRemainingMembersButNotTheLeaver() {
        PartyEvent left = event("party.left", LEADER.toString(), member(LEADER, "Alice"), """
                {"playerId":"%s","playerName":"Bob"}""".formatted(FOLLOWER_IN_LOBBY));

        handler().handle(left);

        assertTrue(lobbyFollower.sent().isEmpty(), "the leaver reads only the command answer");
        assertEquals(1, leader.sent().size(), "the remaining member is told");
    }

    @Test
    void leaveLeavingOneMemberTellsTheRemainingMemberThatThePartyStaysOpen() {
        PartyEvent left = event("party.left", LEADER.toString(), member(LEADER, "Alice"), """
                {"playerId":"%s","playerName":"Bob"}""".formatted(FOLLOWER_IN_LOBBY));

        handler().handle(left);

        assertEquals(List.of(plain(aloneMessage("Bob"))), sentTexts(leader),
                "the single remaining member must get the party.left.alone message, and only that");
    }

    @Test
    void kickLeavingOneMemberTellsTheRemainingLeaderThatThePartyStaysOpen() {
        PartyEvent kicked = event("party.kicked", LEADER.toString(), member(LEADER, "Alice"), """
                {"playerId":"%s","playerName":"Bob"}""".formatted(FOLLOWER_IN_LOBBY));

        handler().handle(kicked);

        assertEquals(List.of(plain(aloneMessage("Bob"))), sentTexts(leader),
                "the leader who kicked, left alone, is told the party stays open; the command answer does not say it");
        assertEquals(1, lobbyFollower.sent().size(), "the kicked player is told");
    }

    @Test
    void leaveWithTwoRemainingKeepsTheGenericLeftMessage() {
        PartyEvent left = event("party.left", LEADER.toString(), """
                %s,%s""".formatted(member(LEADER, "Alice"), member(FOLLOWER_IN_GAME, "Carol")), """
                {"playerId":"%s","playerName":"Bob"}""".formatted(FOLLOWER_IN_LOBBY));

        handler().handle(left);

        String generic = plain(messages.message(Locale.ENGLISH, "party.left", Placeholder.unparsed("player", "Bob")));
        assertEquals(List.of(generic), sentTexts(leader), "the leader must get the generic party.left message");
        assertEquals(List.of(generic), sentTexts(gameFollower), "the other member must get the generic party.left message");
    }

    private Component aloneMessage(String player) {
        return messages.message(Locale.ENGLISH, "party.left.alone", Placeholder.unparsed("player", player));
    }

    private static List<String> sentTexts(FakeLocalPlayer player) {
        return player.sent().stream().map(EventHandlerTest::plain).toList();
    }

    @Test
    void promoteNotifiesTheNewLeaderAndOthersButNotThePreviousLeader() {
        PartyEvent promoted = event("party.leader-changed", FOLLOWER_IN_LOBBY.toString(), """
                %s,%s,%s""".formatted(member(LEADER, "Alice"), member(FOLLOWER_IN_LOBBY, "Bob"),
                member(FOLLOWER_IN_GAME, "Carol")), """
                {"previousLeaderId":"%s","leaderId":"%s"}""".formatted(LEADER, FOLLOWER_IN_LOBBY));

        handler().handle(promoted);

        assertTrue(leader.sent().isEmpty(), "the previous leader reads only the command answer");
        assertEquals(1, lobbyFollower.sent().size(), "the new leader is told");
        assertTrue(plain(lobbyFollower.sent().get(0)).contains("new party leader"), plain(lobbyFollower.sent().get(0)));
        assertEquals(1, gameFollower.sent().size(), "the other member is told");
    }

    @Test
    void denyNotifiesTheMembersButNotTheDecliner() {
        PartyEvent denied = event("party.denied", LEADER.toString(), member(LEADER, "Alice"), """
                {"playerId":"%s","playerName":"Bob"}""".formatted(FOLLOWER_IN_LOBBY));

        handler().handle(denied);

        assertTrue(lobbyFollower.sent().isEmpty(), "the decliner reads only the command answer");
        assertEquals(1, leader.sent().size(), "the leader is told");
    }

    @Test
    void inviteButtonsExplainThemselvesOnHover() {
        PartyEvent invited = event("party.invited", LEADER.toString(), "", """
                {"inviterId":"%s","inviterName":"Alice","targetId":"%s","targetName":"Bob",
                 "expiresAt":"2026-10-28T10:01:00Z"}""".formatted(LEADER, FOLLOWER_IN_LOBBY));

        handler().handle(invited);

        assertTrue(hovers(lobbyFollower.sent().get(0)) >= 2, "[Accept] and [Deny] both have hover text");
    }

    private static int hovers(Component component) {
        int count = component.hoverEvent() == null ? 0 : 1;
        for (Component child : component.children()) {
            count += hovers(child);
        }
        return count;
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
        assertEquals("Party │ Alice » gg", plain(leader.sent().get(0)));
        assertEquals("Party │ Alice » gg", plain(german.sent().get(0)), "chat lines carry no translated text");
    }

    @Test
    void unknownEventTypesAreIgnored() {
        PartyEvent future = event("party.teleported-to-moon", LEADER.toString(), """
                %s""".formatted(member(LEADER, "Alice")), "{}");

        handler().handle(future);

        assertFalse(leader.sent().iterator().hasNext(), "nothing is sent for an unknown event type");
    }
}
