package net.onelitefeather.quelea.velocity.command;

import net.onelitefeather.quelea.velocity.http.Invite;
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
    final List<String> accepted = new ArrayList<>();

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
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Invite> invites(String playerId) {
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
