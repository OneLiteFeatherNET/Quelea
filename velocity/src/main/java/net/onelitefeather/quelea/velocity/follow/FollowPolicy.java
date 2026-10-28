package net.onelitefeather.quelea.velocity.follow;

import java.util.regex.Pattern;

/**
 * Decides when a party leader's move starts a warp and which members follow a warp. Pure: no proxy calls.
 */
public final class FollowPolicy {

    private final Pattern lobbyPattern;
    private final FollowMode mode;

    public FollowPolicy(Pattern lobbyPattern, FollowMode mode) {
        this.lobbyPattern = lobbyPattern;
        this.mode = mode;
    }

    public static FollowPolicy of(String lobbyRegex, FollowMode mode) {
        return new FollowPolicy(Pattern.compile(lobbyRegex), mode);
    }

    /**
     * @param server a server name, may be {@code null}
     * @return whether the server name matches the lobby pattern (regex find)
     */
    public boolean isLobby(String server) {
        return server != null && lobbyPattern.matcher(server).find();
    }

    /**
     * @param newServer the server the leader just connected to
     * @return whether that move is a game the party should follow to
     */
    public boolean shouldTriggerWarp(String newServer) {
        return newServer != null && !isLobby(newServer);
    }

    /**
     * @param memberCurrentServer the server the member is on now, {@code null} when unknown
     * @param target              the server the party was warped to
     * @return whether the member should be connected to {@code target}
     */
    public boolean shouldFollow(String memberCurrentServer, String target) {
        if (target == null || target.equals(memberCurrentServer)) {
            return false;
        }
        return switch (mode) {
            case LOBBY -> isLobby(memberCurrentServer);
            case ALWAYS -> true;
        };
    }
}
