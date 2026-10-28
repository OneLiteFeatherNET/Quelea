package net.onelitefeather.quelea.backend.party;

/**
 * Entity tags of a party: the strong tag of version {@code n} is {@code "n"} (RFC 9110 section 8.8.3). Comparison
 * follows RFC 9110 section 13.1: {@code If-Match} uses the strong comparison, under which a weak tag never matches;
 * {@code If-None-Match} uses the weak comparison, which ignores the {@code W/} prefix.
 */
public final class EntityTags {

    private static final String ANY = "*";
    private static final String WEAK_PREFIX = "W/";

    private EntityTags() {
    }

    public static String of(long version) {
        return "\"" + version + "\"";
    }

    /** True when a listed tag strongly matches the version, or the header is {@code *}. */
    public static boolean matchesStrong(String header, long version) {
        return matches(header, version, false);
    }

    /** True when a listed tag weakly matches the version, or the header is {@code *}. */
    public static boolean matchesWeak(String header, long version) {
        return matches(header, version, true);
    }

    private static boolean matches(String header, long version, boolean weak) {
        String expected = of(version);
        for (String listed : header.split(",")) {
            String tag = listed.trim();
            if (ANY.equals(tag)) {
                return true;
            }
            if (tag.startsWith(WEAK_PREFIX)) {
                if (!weak) {
                    continue;
                }
                tag = tag.substring(WEAK_PREFIX.length());
            }
            if (expected.equals(tag)) {
                return true;
            }
        }
        return false;
    }
}
