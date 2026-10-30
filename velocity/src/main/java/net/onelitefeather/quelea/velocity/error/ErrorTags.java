package net.onelitefeather.quelea.velocity.error;

import java.util.HashMap;
import java.util.Map;

/**
 * The tags every report carries. Only proxy and party ids go in; player names, player ids and headers never do.
 */
public final class ErrorTags {

    public static final String COMPONENT = "component";
    public static final String COMPONENT_VELOCITY = "velocity";
    public static final String PROXY_ID = "proxy_id";
    public static final String PARTY_ID = "party_id";

    private ErrorTags() {
    }

    /**
     * @param proxyId this proxy's id
     * @param partyId the party the failure concerns, or {@code null} when it is not known
     * @return an unmodifiable map with {@code component}, {@code proxy_id} and, when known, {@code party_id}
     */
    public static Map<String, String> of(String proxyId, String partyId) {
        Map<String, String> tags = new HashMap<>();
        tags.put(COMPONENT, COMPONENT_VELOCITY);
        tags.put(PROXY_ID, proxyId);
        if (partyId != null) {
            tags.put(PARTY_ID, partyId);
        }
        return Map.copyOf(tags);
    }
}
