package net.onelitefeather.quelea.velocity.listener;

import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.onelitefeather.quelea.velocity.error.FailureReports;
import net.onelitefeather.quelea.velocity.error.FakeErrorReporter;
import net.onelitefeather.quelea.velocity.http.Invite;
import net.onelitefeather.quelea.velocity.i18n.Messages;
import net.onelitefeather.quelea.velocity.http.Member;
import net.onelitefeather.quelea.velocity.http.Party;
import net.onelitefeather.quelea.velocity.http.PlayerRef;
import net.onelitefeather.quelea.velocity.http.QueleaGateway;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresenceListenerTest {

    private static final UUID LEADER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final Instant JOINED = Instant.parse("2026-10-28T10:00:00Z");

    private final RecordingGateway gateway = new RecordingGateway();
    private final PresenceListener listener = new PresenceListener(gateway, Runnable::run, "proxy-1",
            new FailureReports(new FakeErrorReporter(), Messages.load(null, LoggerFactory.getLogger(PresenceListenerTest.class)), "proxy-1"),
            LoggerFactory.getLogger(PresenceListenerTest.class));

    /** A party led by Alice with Bob in it; Bob's connections are the member case. */
    private void leaderOfAParty() {
        gateway.party = Optional.of(new Party("p-1", LEADER.toString(),
                List.of(new Member(LEADER.toString(), "Alice", JOINED), new Member(MEMBER.toString(), "Bob", JOINED)),
                JOINED));
    }

    private void connect(UUID player, String previous, String target) {
        listener.onServerConnected(new ServerConnectedEvent(stub(Player.class, Map.of(
                        "getUniqueId", player, "getUsername", player.equals(LEADER) ? "Alice" : "Bob")),
                server(target), previous == null ? null : server(previous)));
    }

    @Test
    void leaderLeavingAGameWarpsThePartyToTheLobbyWithTheGameAsOrigin() {
        leaderOfAParty();

        connect(LEADER, "Bounce-2", "Lobby-2");

        assertEquals(List.of("p-1:Lobby-2:Bounce-2"), gateway.warps(), "the warp names the target and the origin");
    }

    @Test
    void leaderJoiningAGameWarpsThePartyWithTheLobbyAsOrigin() {
        leaderOfAParty();

        connect(LEADER, "Lobby-1", "Bounce-2");

        assertEquals(List.of("p-1:Bounce-2:Lobby-1"), gateway.warps());
    }

    @Test
    void leaderMovingBetweenLobbiesWarpsTheParty() {
        leaderOfAParty();

        connect(LEADER, "Lobby-1", "Lobby-2");

        assertEquals(List.of("p-1:Lobby-2:Lobby-1"), gateway.warps(), "lobby moves warp too, so the party aligns");
    }

    @Test
    void firstConnectionAfterLoginRequestsNoWarp() {
        leaderOfAParty();

        connect(LEADER, null, "Lobby-1");

        assertTrue(gateway.warps().isEmpty(), "a login has no previous server and warps nothing");
    }

    @Test
    void memberMovingServersRequestsNoWarp() {
        leaderOfAParty();

        connect(MEMBER, "Lobby-1", "Bounce-2");

        assertTrue(gateway.warps().isEmpty(), "only the leader warps the party");
    }

    /** Answers the party lookup from a field and records the warps; every other route is unsupported. */
    private static final class RecordingGateway implements QueleaGateway {

        private Optional<Party> party = Optional.empty();
        private final List<String> warps = new ArrayList<>();

        List<String> warps() {
            return List.copyOf(warps);
        }

        @Override
        public Optional<Party> partyOf(String playerId) {
            return party;
        }

        @Override
        public void warp(String partyId, String actorId, String server, String fromServer) {
            warps.add(partyId + ":" + server + ":" + fromServer);
        }

        @Override
        public Party createParty(String leaderId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Invite invite(String actorId, String targetName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Invite> invites(String playerId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Party acceptInvite(String playerId, String partyId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void denyInvite(String playerId, String partyId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void leave(String playerId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Party kick(String partyId, String actorId, String targetId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Party promote(String partyId, String actorId, String targetId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void disband(String partyId, String actorId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void chat(String partyId, String actorId, String message) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Party setPublic(String partyId, String actorId, boolean open) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Party join(String playerId, String targetName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public net.onelitefeather.quelea.velocity.http.JoinResult request(String playerId, String targetName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Party acceptRequest(String partyId, String actorId, String requesterName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void rejectRequest(String partyId, String actorId, String requesterName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void snapshot(String proxyId, List<PlayerRef> players) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void login(String proxyId, String playerId, String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void disconnect(String proxyId, String playerId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void heartbeat(String proxyId) {
            throw new UnsupportedOperationException();
        }
    }

    private static RegisteredServer server(String name) {
        return stub(RegisteredServer.class, Map.of("getServerInfo",
                new ServerInfo(name, new InetSocketAddress("127.0.0.1", 25565))));
    }

    /** A Velocity interface answering the given getters; the proxy never calls anything else. */
    private static <T> T stub(Class<T> type, Map<String, Object> answers) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> answers.get(method.getName())));
    }
}
