package net.onelitefeather.quelea.velocity.event;

import com.google.gson.JsonObject;
import net.onelitefeather.quelea.velocity.http.Member;
import net.onelitefeather.quelea.velocity.http.QueleaJson;

import java.time.Instant;
import java.util.List;

/**
 * A party event as the service publishes it on the event stream.
 *
 * @param id      the event's UUID
 * @param type    e.g. {@code party.joined}
 * @param partyId the party the event is about
 * @param leaderId the leader after the change
 * @param members the members after the change (empty for {@code party.disbanded})
 * @param payload the type-specific fields
 */
public record PartyEvent(String id, String type, String partyId, Instant occurredAt, String leaderId,
                         List<Member> members, JsonObject payload) {

    public PartyEvent {
        members = List.copyOf(members);
    }

    /**
     * @param json the event's {@code data} field
     * @return the parsed event
     * @throws IllegalArgumentException when the JSON does not describe an event
     */
    public static PartyEvent parse(String json) {
        JsonObject object = QueleaJson.object(json);
        JsonObject payload = object.has("payload") && object.get("payload").isJsonObject()
                ? object.getAsJsonObject("payload")
                : new JsonObject();
        return new PartyEvent(
                QueleaJson.string(object, "id"),
                QueleaJson.string(object, "type"),
                QueleaJson.string(object, "partyId"),
                Instant.parse(QueleaJson.string(object, "occurredAt")),
                QueleaJson.optionalString(object, "leaderId"),
                object.has("members") && object.get("members").isJsonArray()
                        ? QueleaJson.members(object.getAsJsonArray("members"))
                        : List.of(),
                payload);
    }

    /**
     * @return the payload field as a string, or {@code null} when absent
     */
    public String field(String key) {
        return QueleaJson.optionalString(payload, key);
    }
}
