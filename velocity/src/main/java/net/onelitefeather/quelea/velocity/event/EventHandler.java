package net.onelitefeather.quelea.velocity.event;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns party events into messages for the local players they concern, and moves local members on a warp.
 * Unknown event types are ignored.
 */
public final class EventHandler {

    private static final String EXPIRED_ALONE = "expired-alone";
    private static final String DISBANDED_BY_LEADER = "leader";
    private static final String ALONE_KEY = "party.left.alone";

    private final LocalPlayers players;
    private final Messages messages;
    private final FollowPolicy follow;
    private final Clock clock;

    public EventHandler(LocalPlayers players, Messages messages, FollowPolicy follow, Clock clock) {
        this.players = players;
        this.messages = messages;
        this.follow = follow;
        this.clock = clock;
    }

    public void handle(PartyEvent event) {
        switch (event.type()) {
            case "party.created" -> notifyMembers(event, event.leaderId(), null, "party.created",
                    Placeholder.unparsed("player", leaderName(event)));
            case "party.invited" -> invited(event);
            case "party.joined" -> notifyOthers(event, "party.joined", playerName(event));
            case "party.denied" -> notifyOthers(event, "party.denied", playerName(event));
            case "party.left" -> left(event);
            case "party.kicked" -> kicked(event);
            case "party.leader-changed" -> leaderChanged(event);
            case "party.disbanded" -> disbanded(event);
            case "party.expiring" -> expiring(event);
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
            TagResolver inviter = Placeholder.unparsed("inviter", inviterName);
            Component accept = messages.line(player.locale(), "party.invite.accept")
                    .clickEvent(ClickEvent.runCommand("/party accept " + inviterName))
                    .hoverEvent(HoverEvent.showText(
                            messages.line(player.locale(), "party.invite.accept.hover", inviter)));
            Component deny = messages.line(player.locale(), "party.invite.deny")
                    .clickEvent(ClickEvent.runCommand("/party deny " + inviterName))
                    .hoverEvent(HoverEvent.showText(
                            messages.line(player.locale(), "party.invite.deny.hover", inviter)));
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
     * member any more). The actor of the command never gets either: their command answer already says it.
     *
     * @param actorId    the player who ran the command, or {@code null} when the event does not name one
     * @param affectedId the player the change is about, or {@code null}
     */
    private void notifyMembers(PartyEvent event, String actorId, String affectedId, String key,
                               TagResolver... resolvers) {
        if (affectedId != null && !affectedId.equals(actorId)) {
            sendTo(affectedId, key + ".self", resolvers);
        }
        for (Member member : event.members()) {
            if (!member.id().equals(affectedId) && !member.id().equals(actorId)) {
                sendTo(member.id(), key, resolvers);
            }
        }
    }

    /** For events where the actor is the affected player: everyone else in the party hears about it. */
    private void notifyOthers(PartyEvent event, String key, TagResolver... resolvers) {
        String actorId = event.field("playerId");
        notifyMembers(event, actorId, actorId, key, resolvers);
    }

    /** A leave that leaves one member tells that member the party stays open; a larger party keeps the generic line. */
    private void left(PartyEvent event) {
        if (event.members().size() == 1) {
            sendTo(event.members().get(0).id(), ALONE_KEY, playerName(event));
            return;
        }
        notifyOthers(event, "party.left", playerName(event));
    }

    /**
     * A kick that leaves one member: the kicked player gets the self line, and the remaining member (the kicking
     * leader, who is the actor) gets the alone line, which the command answer does not give.
     */
    private void kicked(PartyEvent event) {
        if (event.members().size() != 1) {
            notifyMembers(event, event.leaderId(), event.field("playerId"), "party.kicked", playerName(event));
            return;
        }
        String kickedId = event.field("playerId");
        if (kickedId != null) {
            sendTo(kickedId, "party.kicked.self", playerName(event));
        }
        sendTo(event.members().get(0).id(), ALONE_KEY, playerName(event));
    }

    private void leaderChanged(PartyEvent event) {
        String leaderId = event.leaderId();
        String actorId = event.field("previousLeaderId");
        if (leaderId != null && !leaderId.equals(actorId)) {
            sendTo(leaderId, "party.leader-changed.self");
        }
        for (Member member : event.members()) {
            if (!member.id().equals(leaderId) && !member.id().equals(actorId)) {
                sendTo(member.id(), "party.leader-changed", Placeholder.unparsed("player", leaderName(event)));
            }
        }
    }

    private void disbanded(PartyEvent event) {
        if (!event.payload().has("formerMembers") || !event.payload().get("formerMembers").isJsonArray()) {
            return;
        }
        // Unknown reasons keep the generic message, so the service can add reasons without a plugin release.
        String reason = event.field("reason");
        String key = EXPIRED_ALONE.equals(reason) ? "party.disbanded.expired-alone" : "party.disbanded";
        // A leader who disbanded the party already read the command's answer.
        String actor = DISBANDED_BY_LEADER.equals(reason) ? event.leaderId() : null;
        for (PlayerRef former : QueleaJson.players(event.payload().getAsJsonArray("formerMembers"))) {
            if (!former.id().equals(actor)) {
                sendTo(former.id(), key);
            }
        }
    }

    /** Tells the leader, if local, how many minutes the lonely party has left, rounded up. */
    private void expiring(PartyEvent event) {
        String leaderId = event.leaderId();
        String expiresAt = event.field("expiresAt");
        if (leaderId == null || expiresAt == null) {
            return;
        }
        Instant expiry;
        try {
            expiry = Instant.parse(expiresAt);
        } catch (DateTimeParseException e) {
            return;
        }
        long seconds = Math.max(0, Duration.between(clock.instant(), expiry).getSeconds());
        long minutes = Math.max(1, (seconds + 59) / 60);
        sendTo(leaderId, "party.expiring", Placeholder.unparsed("minutes", Long.toString(minutes)));
    }

    private void warp(PartyEvent event) {
        String server = event.field("server");
        if (server == null) {
            return;
        }
        // Absent from warps of services that do not name the origin; the lobby rule then applies alone.
        String origin = event.field("from");
        for (Member member : event.members()) {
            if (member.id().equals(event.leaderId())) {
                continue;
            }
            UUID id = uuid(member.id());
            Optional<LocalPlayer> local = id == null ? Optional.empty() : players.find(id);
            local.ifPresent(player -> {
                if (!follow.shouldFollow(player.currentServer().orElse(null), server, origin)) {
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
