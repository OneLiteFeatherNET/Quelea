package net.onelitefeather.quelea.velocity.follow;

/**
 * Which local party members follow a warp: {@code lobby} only those in a lobby, {@code always} everyone.
 */
public enum FollowMode {
    LOBBY,
    ALWAYS;

    /**
     * @param value {@code lobby} or {@code always}, ignoring case
     * @return the mode
     * @throws IllegalArgumentException for any other value
     */
    public static FollowMode parse(String value) {
        for (FollowMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value == null ? "" : value.trim())) {
                return mode;
            }
        }
        throw new IllegalArgumentException("unknown follow mode '" + value + "', expected lobby or always");
    }
}
