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

    /**
     * @param fromServer the server the leader left, or {@code null} when the warp does not name one
     */
    void warp(String partyId, String actorId, String server, String fromServer);

    void chat(String partyId, String actorId, String message);

    /**
     * Opens ({@code open = true}) or closes the leader's party to everyone.
     */
    Party setPublic(String partyId, String actorId, boolean open);

    /**
     * Joins the party of {@code targetName} directly; the service refuses it unless the party is public.
     */
    Party join(String playerId, String targetName);

    /**
     * Asks the party of {@code targetName} to let the player in: a public party admits them at once, a private one
     * holds the request for its leader.
     */
    JoinResult request(String playerId, String targetName);

    Party acceptRequest(String partyId, String actorId, String requesterName);

    void rejectRequest(String partyId, String actorId, String requesterName);

    void snapshot(String proxyId, List<PlayerRef> players);

    void login(String proxyId, String playerId, String name);

    void disconnect(String proxyId, String playerId);

    void heartbeat(String proxyId);
}
