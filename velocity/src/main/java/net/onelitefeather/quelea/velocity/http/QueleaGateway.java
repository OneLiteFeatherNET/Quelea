package net.onelitefeather.quelea.velocity.http;

import java.util.List;
import java.util.Optional;

/**
 * One method per route of the party service. Every method blocks until the service answered; callers run them
 * off the proxy's event threads. Failures are {@link QueleaProblemException} (the service said no) or
 * {@link QueleaUnavailableException} (the service could not be reached or answered something unusable).
 */
public interface QueleaGateway {

    Party createParty(String leaderId);

    /**
     * @return the player's party, or empty when the player is not in one
     */
    Optional<Party> partyOf(String playerId);

    Invite invite(String actorId, String targetName);

    List<Invite> invites(String playerId);

    Party acceptInvite(String playerId, String partyId);

    void denyInvite(String playerId, String partyId);

    void leave(String playerId);

    Party kick(String partyId, String actorId, String targetId);

    Party promote(String partyId, String actorId, String targetId);

    void disband(String partyId, String actorId);

    void warp(String partyId, String actorId, String server);

    void chat(String partyId, String actorId, String message);

    void snapshot(String proxyId, List<PlayerRef> players);

    void login(String proxyId, String playerId, String name);

    void disconnect(String proxyId, String playerId);

    void heartbeat(String proxyId);
}
