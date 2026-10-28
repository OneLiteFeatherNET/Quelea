package net.onelitefeather.quelea.velocity.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps the service's JSON onto the records of this package. Every method throws {@link IllegalArgumentException}
 * when a required field is missing or malformed.
 */
public final class QueleaJson {

    private QueleaJson() {
    }

    public static JsonObject object(String json) {
        JsonElement element = JsonParser.parseString(json);
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("expected a JSON object");
        }
        return element.getAsJsonObject();
    }

    public static JsonArray array(String json) {
        JsonElement element = JsonParser.parseString(json);
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("expected a JSON array");
        }
        return element.getAsJsonArray();
    }

    public static Party party(JsonObject json) {
        return new Party(
                string(json, "id"),
                string(json, "leaderId"),
                members(json.getAsJsonArray("members")),
                Instant.parse(string(json, "createdAt")));
    }

    public static Invite invite(JsonObject json) {
        return new Invite(
                string(json, "partyId"),
                string(json, "inviterId"),
                string(json, "inviterName"),
                string(json, "targetId"),
                string(json, "targetName"),
                Instant.parse(string(json, "expiresAt")));
    }

    public static List<Member> members(JsonArray array) {
        List<Member> members = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            JsonObject member = element.getAsJsonObject();
            members.add(new Member(
                    string(member, "id"),
                    string(member, "name"),
                    member.has("joinedAt") ? Instant.parse(string(member, "joinedAt")) : null));
        }
        return members;
    }

    public static List<PlayerRef> players(JsonArray array) {
        List<PlayerRef> players = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            JsonObject player = element.getAsJsonObject();
            players.add(new PlayerRef(string(player, "id"), string(player, "name")));
        }
        return players;
    }

    /**
     * @return the string value of a required field
     * @throws IllegalArgumentException when the field is missing, null or not a string
     */
    public static String string(JsonObject json, String key) {
        String value = optionalString(json, key);
        if (value == null) {
            throw new IllegalArgumentException("missing field " + key);
        }
        return value;
    }

    /**
     * @return the string value of an optional field, or {@code null}
     */
    public static String optionalString(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return null;
        }
        return element.getAsString();
    }
}
