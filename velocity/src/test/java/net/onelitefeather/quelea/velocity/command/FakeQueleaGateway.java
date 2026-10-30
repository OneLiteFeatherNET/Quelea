package net.onelitefeather.quelea.velocity.command;

import net.onelitefeather.quelea.velocity.http.Invite;
import net.onelitefeather.quelea.velocity.http.JoinResult;
import net.onelitefeather.quelea.velocity.http.Party;
import net.onelitefeather.quelea.velocity.http.PlayerRef;
import net.onelitefeather.quelea.velocity.http.QueleaGateway;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Answers from fixed state and records the accepted invites. Every other route is unsupported by these tests.
 */
final class FakeQueleaGateway implements QueleaGateway {

    private final List<Invite> invites = new ArrayList<>();
    private final List<Party> parties = new ArrayList<>();
    private RuntimeException failure;
    final List<String> accepted = new ArrayList<>();

    /** Every invite and invite-list call fails with this exception. */
    FakeQueleaGateway failingWith(RuntimeException failure) {
        this.failure = failure;
        return this;
    }

    FakeQueleaGateway withInvite(Invite invite) {
        invites.add(invite);
        return this;
    }

    FakeQueleaGateway withParty(Party party) {
        parties.add(party);
        return this;
    }

    @Override
    public Party createParty(String leaderId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Party> partyOf(String playerId) {
        return parties.stream()
                .filter(party -> party.members().stream().anyMatch(member -> member.id().equals(playerId)))
                .findFirst();
    }

    @Override
    public Invite invite(String actorId, String targetName) {
        if (failure != null) {
            throw failure;
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Invite> invites(String playerId) {
        if (failure != null) {
            throw failure;
        }
        return invites.stream().filter(invite -> invite.targetId().equals(playerId)).toList();
    }

    @Override
    public Party acceptInvite(String playerId, String partyId) {
        accepted.add(partyId);
        return parties.stream().filter(party -> party.id().equals(partyId)).findFirst().orElseThrow();
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
    public void warp(String partyId, String actorId, String server, String fromServer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void chat(String partyId, String actorId, String message) {
        throw new UnsupportedOperationException();
    }

    /** The service calls that changed the public flag: {@code "on"} or {@code "off"}, in order. */
    final List<String> publicCalls = new ArrayList<>();
    /** Names passed to {@code join}, in order. */
    final List<String> joins = new ArrayList<>();
    /** Names passed to {@code request}, in order. */
    final List<String> requests = new ArrayList<>();
    /** {@code accept:<name>} and {@code reject:<name>} for each answer, in order. */
    final List<String> answers = new ArrayList<>();
    private JoinResult requestResult;

    FakeQueleaGateway withRequestResult(JoinResult result) {
        this.requestResult = result;
        return this;
    }

    @Override
    public Party setPublic(String partyId, String actorId, boolean open) {
        if (failure != null) {
            throw failure;
        }
        publicCalls.add(open ? "on" : "off");
        return parties.stream().filter(party -> party.id().equals(partyId)).findFirst().orElseThrow();
    }

    @Override
    public Party join(String playerId, String targetName) {
        if (failure != null) {
            throw failure;
        }
        joins.add(targetName);
        return parties.get(0);
    }

    @Override
    public JoinResult request(String playerId, String targetName) {
        if (failure != null) {
            throw failure;
        }
        requests.add(targetName);
        return requestResult;
    }

    @Override
    public Party acceptRequest(String partyId, String actorId, String requesterName) {
        answers.add("accept:" + requesterName);
        return parties.stream().filter(party -> party.id().equals(partyId)).findFirst().orElseThrow();
    }

    @Override
    public void rejectRequest(String partyId, String actorId, String requesterName) {
        answers.add("reject:" + requesterName);
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
