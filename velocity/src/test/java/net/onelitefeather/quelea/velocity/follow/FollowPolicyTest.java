package net.onelitefeather.quelea.velocity.follow;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FollowPolicyTest {

    private static final String LOBBY_PATTERN = "(?i)^lobby.*";

    private static FollowPolicy lobbyMode() {
        return FollowPolicy.of(LOBBY_PATTERN, FollowMode.LOBBY);
    }

    private static FollowPolicy alwaysMode() {
        return FollowPolicy.of(LOBBY_PATTERN, FollowMode.ALWAYS);
    }

    @Nested
    class LobbyDetection {

        @Test
        void recognisesLobbyServerNames() {
            assertTrue(lobbyMode().isLobby("Lobby-1"));
        }

        @Test
        void matchesTheLobbyPatternIgnoringCase() {
            assertTrue(lobbyMode().isLobby("lobby-2"));
        }

        @Test
        void doesNotTreatGameServersAsLobbies() {
            assertFalse(lobbyMode().isLobby("Bounce-2"));
        }

        @Test
        void unknownServerIsNotALobby() {
            assertFalse(lobbyMode().isLobby(null));
        }
    }

    @Nested
    class WarpTrigger {

        @Test
        void gameServerTriggersAWarp() {
            assertTrue(lobbyMode().shouldTriggerWarp("Bounce-2"));
        }

        @Test
        void returningToTheLobbyTriggersNoWarp() {
            assertFalse(lobbyMode().shouldTriggerWarp("Lobby-2"));
        }

        @Test
        void unknownServerTriggersNoWarp() {
            assertFalse(lobbyMode().shouldTriggerWarp(null));
        }
    }

    @Nested
    class LobbyMode {

        @Test
        void memberInALobbyFollowsToTheTarget() {
            assertTrue(lobbyMode().shouldFollow("Lobby-1", "Bounce-2"));
        }

        @Test
        void memberInAnotherGameStaysPut() {
            assertFalse(lobbyMode().shouldFollow("Tamias-1", "Bounce-2"));
        }

        @Test
        void memberAlreadyOnTheTargetIsNotConnectedAgain() {
            assertFalse(lobbyMode().shouldFollow("Bounce-2", "Bounce-2"));
        }

        @Test
        void memberOfUnknownServerDoesNotFollow() {
            assertFalse(lobbyMode().shouldFollow(null, "Bounce-2"));
        }

        @Test
        void noTargetMeansNoMove() {
            assertFalse(lobbyMode().shouldFollow("Lobby-1", null));
        }
    }

    @Nested
    class AlwaysMode {

        @Test
        void memberInALobbyFollows() {
            assertTrue(alwaysMode().shouldFollow("Lobby-1", "Bounce-2"));
        }

        @Test
        void memberInAnotherGameFollows() {
            assertTrue(alwaysMode().shouldFollow("Tamias-1", "Bounce-2"));
        }

        @Test
        void memberOfUnknownServerFollows() {
            assertTrue(alwaysMode().shouldFollow(null, "Bounce-2"));
        }

        @Test
        void memberAlreadyOnTheTargetDoesNotFollow() {
            assertFalse(alwaysMode().shouldFollow("Bounce-2", "Bounce-2"));
        }
    }
}
