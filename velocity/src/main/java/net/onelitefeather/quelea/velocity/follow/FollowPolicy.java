package net.onelitefeather.quelea.velocity.follow;

import java.util.regex.Pattern;

/**
 * Decides which members follow a warp. Pure: no proxy calls.
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
     * A member follows when mode is {@code always}, when the member was on the server the leader left (they were
     * together), or when the member is in a lobby. The leader and members already on the target are never moved;
     * the caller checks the leader by id. Without an origin (a warp from a service that does not send one) only the
     * lobby rule applies.
     *
     * @param memberCurrentServer the server the member is on now, {@code null} when unknown
     * @param target              the server the party was warped to
     * @param origin              the server the leader left, {@code null} when the warp does not name one
     * @return whether the member should be connected to {@code target}
     */
    public boolean shouldFollow(String memberCurrentServer, String target, String origin) {
        if (target == null || target.equals(memberCurrentServer)) {
            return false;
        }
        return switch (mode) {
            case ALWAYS -> true;
            case LOBBY -> (origin != null && origin.equals(memberCurrentServer)) || isLobby(memberCurrentServer);
        };
    }
}
