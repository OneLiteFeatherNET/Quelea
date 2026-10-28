package net.onelitefeather.quelea.velocity.event;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.onelitefeather.quelea.velocity.follow.FollowPolicy;
import net.onelitefeather.quelea.velocity.http.Member;
import net.onelitefeather.quelea.velocity.http.PlayerRef;
import net.onelitefeather.quelea.velocity.http.QueleaJson;
import net.onelitefeather.quelea.velocity.i18n.Messages;
import net.onelitefeather.quelea.velocity.player.ConnectOutcome;
import net.onelitefeather.quelea.velocity.player.LocalPlayer;
import net.onelitefeather.quelea.velocity.player.LocalPlayers;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns party events into messages for the local players they concern, and moves local members on a warp.
 * Unknown event types are ignored.
 */
public final class EventHandler {

    private final LocalPlayers players;
    private final Messages messages;
    private final FollowPolicy follow;

    public EventHandler(LocalPlayers players, Messages messages, FollowPolicy follow) {
        this.players = players;
        this.messages = messages;
        this.follow = follow;
    }

    public void handle(PartyEvent event) {
        switch (event.type()) {
            case "party.created" -> notifyMembers(event, null, "party.created", Placeholder.unparsed("player", leaderName(event)));
            case "party.invited" -> invited(event);
            case "party.joined" -> notifyMembers(event, event.field("playerId"), "party.joined", playerName(event));
            case "party.denied" -> notifyMembers(event, event.field("playerId"), "party.denied", playerName(event));
            case "party.left" -> notifyMembers(event, event.field("playerId"), "party.left", playerName(event));
            case "party.kicked" -> notifyMembers(event, event.field("playerId"), "party.kicked", playerName(event));
            case "party.leader-changed" -> leaderChanged(event);
            case "party.disbanded" -> disbanded(event);
            case "party.warp" -> warp(event);
            case "party.chat" -> chat(event);
            default -> {
                // Unknown types are ignored on purpose: the service may add events before the plugin knows them.
            }
        }
    }

    private void invited(PartyEvent event) {
        String inviterName = event.field("inviterName");
        UUID target = uuid(event.field("targetId"));
        if (target == null || inviterName == null) {
            return;
        }
        players.find(target).ifPresent(player -> {
            Component accept = messages.line(player.locale(), "party.invite.accept")
                    .clickEvent(ClickEvent.runCommand("/party accept " + inviterName));
            Component deny = messages.line(player.locale(), "party.invite.deny")
                    .clickEvent(ClickEvent.runCommand("/party deny " + inviterName));
            player.send(messages.message(player.locale(), "party.invited",
                            Placeholder.unparsed("inviter", inviterName))
                    .append(Component.space())
                    .append(accept)
                    .append(Component.space())
                    .append(deny));
        });
    }

    /**
     * Sends {@code key} to the members of the event, and {@code key.self} to the affected player (who may not be a
     * member any more). The affected player never gets both.
     */
    private void notifyMembers(PartyEvent event, String affectedId, String key, TagResolver... resolvers) {
        if (affectedId != null) {
            sendTo(affectedId, key + ".self", resolvers);
        }
        for (Member member : event.members()) {
            if (!member.id().equals(affectedId)) {
                sendTo(member.id(), key, resolvers);
            }
        }
    }

    private void leaderChanged(PartyEvent event) {
        String leaderId = event.leaderId();
        if (leaderId != null) {
            sendTo(leaderId, "party.leader-changed.self");
        }
        for (Member member : event.members()) {
            if (!member.id().equals(leaderId)) {
                sendTo(member.id(), "party.leader-changed", Placeholder.unparsed("player", leaderName(event)));
            }
        }
    }

    private void disbanded(PartyEvent event) {
        if (!event.payload().has("formerMembers") || !event.payload().get("formerMembers").isJsonArray()) {
            return;
        }
        for (PlayerRef former : QueleaJson.players(event.payload().getAsJsonArray("formerMembers"))) {
            sendTo(former.id(), "party.disbanded");
        }
    }

    private void warp(PartyEvent event) {
        String server = event.field("server");
        if (server == null) {
            return;
        }
        for (Member member : event.members()) {
            if (member.id().equals(event.leaderId())) {
                continue;
            }
            UUID id = uuid(member.id());
            Optional<LocalPlayer> local = id == null ? Optional.empty() : players.find(id);
            local.ifPresent(player -> {
                if (!follow.shouldFollow(player.currentServer().orElse(null), server)) {
                    return;
                }
                player.connect(server).thenAccept(outcome -> reportWarp(player, server, outcome));
            });
        }
    }

    private void reportWarp(LocalPlayer player, String server, ConnectOutcome outcome) {
        switch (outcome) {
            case CONNECTED -> {
                // The move itself is the feedback.
            }
            case UNKNOWN_SERVER -> player.send(messages.message(player.locale(), "party.warp.unknown-server",
                    Placeholder.unparsed("server", server)));
            case REFUSED -> player.send(messages.message(player.locale(), "party.warp.refused",
                    Placeholder.unparsed("server", server)));
        }
    }

    private void chat(PartyEvent event) {
        String sender = event.field("senderName");
        String message = event.field("message");
        if (sender == null || message == null) {
            return;
        }
        for (Member member : event.members()) {
            sendLine(member.id(), "party.chat",
                    Placeholder.unparsed("sender", sender),
                    Placeholder.unparsed("message", message));
        }
    }

    private static TagResolver playerName(PartyEvent event) {
        return Placeholder.unparsed("player", Objects.requireNonNullElse(event.field("playerName"), "?"));
    }

    private static String leaderName(PartyEvent event) {
        String leaderId = event.leaderId();
        return event.members().stream()
                .filter(member -> member.id().equals(leaderId))
                .map(Member::name)
                .findFirst()
                .orElse("?");
    }

    private void sendTo(String playerId, String key, TagResolver... resolvers) {
        UUID id = uuid(playerId);
        if (id == null) {
            return;
        }
        players.find(id).ifPresent(player ->
                player.send(messages.message(player.locale(), key, resolvers)));
    }

    private void sendLine(String playerId, String key, TagResolver... resolvers) {
        UUID id = uuid(playerId);
        if (id == null) {
            return;
        }
        players.find(id).ifPresent(player ->
                player.send(messages.line(player.locale(), key, resolvers)));
    }

    private static UUID uuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
