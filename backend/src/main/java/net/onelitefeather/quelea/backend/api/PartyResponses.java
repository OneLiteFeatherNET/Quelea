package net.onelitefeather.quelea.backend.api;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import net.onelitefeather.quelea.backend.party.EntityTags;
import net.onelitefeather.quelea.backend.party.Party;

/** Responses that carry a party with its strong {@code ETag}, the party version (RFC 9110 section 8.8.3). */
final class PartyResponses {

    private PartyResponses() {
    }

    /** 200 with the party, or 304 without a body when {@code If-None-Match} names its current tag. */
    static HttpResponse<Party> read(Party party, String ifNoneMatch) {
        String tag = EntityTags.of(party.version());
        if (ifNoneMatch != null && EntityTags.matchesWeak(ifNoneMatch, party.version())) {
            return HttpResponse.<Party>notModified().header(HttpHeaders.ETAG, tag);
        }
        return HttpResponse.ok(party).header(HttpHeaders.ETAG, tag);
    }

    /** 200 with the party after a command. */
    static HttpResponse<Party> ok(Party party) {
        return read(party, null);
    }

    /** 201 with the new party. */
    static HttpResponse<Party> created(Party party) {
        return HttpResponse.created(party).header(HttpHeaders.ETAG, EntityTags.of(party.version()));
    }
}
