package net.onelitefeather.quelea.velocity.command;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.onelitefeather.quelea.velocity.error.FailureReports;
import net.onelitefeather.quelea.velocity.http.Invite;
import net.onelitefeather.quelea.velocity.http.JoinResult;
import net.onelitefeather.quelea.velocity.http.Member;
import net.onelitefeather.quelea.velocity.http.Party;
import net.onelitefeather.quelea.velocity.http.QueleaGateway;
import net.onelitefeather.quelea.velocity.http.QueleaProblemException;
import net.onelitefeather.quelea.velocity.http.QueleaUnavailableException;
import net.onelitefeather.quelea.velocity.i18n.Messages;
import net.onelitefeather.quelea.velocity.player.LocalPlayer;
import org.slf4j.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * What each {@code /party} and {@code /pc} sub-command does. Every action runs on the executor; every outcome,
 * including each problem code and an unreachable service, is answered with a translated message.
 */
public final class PartyActions {

    private final QueleaGateway gateway;
    private final Executor executor;
    private final Messages messages;
    private final FailureReports failures;
    private final Logger logger;
    private final Clock clock;

    public PartyActions(QueleaGateway gateway, Executor executor, Messages messages, FailureReports failures,
                        Logger logger, Clock clock) {
        this.gateway = gateway;
        this.executor = executor;
        this.messages = messages;
        this.failures = failures;
        this.logger = logger;
        this.clock = clock;
    }

    public void create(LocalPlayer player) {
        run(player, locale -> {
            gateway.createParty(player.id().toString());
            player.send(messages.message(locale, "command.created"));
        });
    }

    public void invite(LocalPlayer player, String name) {
        run(player, locale -> {
            gateway.invite(player.id().toString(), name);
            player.send(messages.message(locale, "command.invite.sent", Placeholder.unparsed("name", name)));
        });
    }

    /**
     * @param inviter the inviter's name, or {@code null} to accept the only pending invite
     */
    public void accept(LocalPlayer player, String inviter) {
        run(player, locale -> {
            Optional<Invite> invite = chooseInvite(player, locale, inviter, true);
            if (invite.isEmpty()) {
                return;
            }
            gateway.acceptInvite(player.id().toString(), invite.get().partyId());
            player.send(messages.message(locale, "command.accept.done"));
        });
    }

    /**
     * @param inviter the inviter's name, or {@code null} to decline the only pending invite
     */
    public void deny(LocalPlayer player, String inviter) {
        run(player, locale -> {
            Optional<Invite> invite = chooseInvite(player, locale, inviter, false);
            if (invite.isEmpty()) {
                return;
            }
            gateway.denyInvite(player.id().toString(), invite.get().partyId());
            player.send(messages.message(locale, "command.deny.done"));
        });
    }

    public void leave(LocalPlayer player) {
        run(player, locale -> {
            gateway.leave(player.id().toString());
            player.send(messages.message(locale, "command.leave.done"));
        });
    }

    public void kick(LocalPlayer player, String name) {
        withMember(player, name, (party, target, locale) -> {
            gateway.kick(party.id(), player.id().toString(), target.id());
            player.send(messages.message(locale, "command.kick.done", Placeholder.unparsed("name", target.name())));
        });
    }

    public void promote(LocalPlayer player, String name) {
        withMember(player, name, (party, target, locale) -> {
            gateway.promote(party.id(), player.id().toString(), target.id());
            player.send(messages.message(locale, "command.promote.done", Placeholder.unparsed("name", target.name())));
        });
    }

    public void disband(LocalPlayer player) {
        withParty(player, (party, locale) -> {
            gateway.disband(party.id(), player.id().toString());
            player.send(messages.message(locale, "command.disband.done"));
        });
    }

    public void list(LocalPlayer player) {
        withParty(player, (party, locale) -> {
            player.send(messages.message(locale, "command.list.header", Placeholder.unparsed("count",
                    String.valueOf(party.members().size()))));
            for (Member member : party.members()) {
                String key = member.id().equals(party.leaderId()) ? "command.list.leader" : "command.list.member";
                player.send(messages.line(locale, key, Placeholder.unparsed("name", member.name())));
            }
        });
    }

    /**
     * Warps the party to the server the caller is on. The service checks that the caller leads the party.
     */
    public void warp(LocalPlayer player) {
        withParty(player, (party, locale) -> {
            Optional<String> server = player.currentServer();
            if (server.isEmpty()) {
                player.send(messages.message(locale, "command.warp.no-server"));
                return;
            }
            gateway.warp(party.id(), player.id().toString(), server.get(), null);
            player.send(messages.message(locale, "command.warp.done", Placeholder.unparsed("server", server.get())));
        });
    }

    /**
     * Sends a party chat message. The message reaches every member, the sender included, as a party event.
     */
    public void chat(LocalPlayer player, String message) {
        withParty(player, (party, locale) ->
                gateway.chat(party.id(), player.id().toString(), message));
    }

    /**
     * Opens the leader's party. Without the Velocity permission the answer is given here and the service is not called.
     */
    public void publicOn(LocalPlayer player) {
        if (!player.hasPermission(LocalPlayer.PUBLIC_PARTY_PERMISSION)) {
            run(player, locale -> player.send(messages.message(locale, "command.public.no-permission")));
            return;
        }
        withParty(player, (party, locale) -> {
            gateway.setPublic(party.id(), player.id().toString(), true);
            player.send(messages.message(locale, "command.public.opened"));
        });
    }

    /** Closes the party. Any leader may close it, with or without the permission. */
    public void publicOff(LocalPlayer player) {
        withParty(player, (party, locale) -> {
            gateway.setPublic(party.id(), player.id().toString(), false);
            player.send(messages.message(locale, "command.public.closed"));
        });
    }

    /**
     * Asks the party of {@code name} to let the player in. A public party answers that the player joined; a private one
     * answers that the request was sent and for how long it stays open.
     */
    public void request(LocalPlayer player, String name) {
        run(player, locale -> {
            JoinResult result = gateway.request(player.id().toString(), name);
            switch (result) {
                case JoinResult.Joined joined -> player.send(messages.message(locale, "command.request.joined",
                        Placeholder.unparsed("name", name)));
                case JoinResult.Sent sent -> player.send(messages.message(locale, "command.request.sent",
                        Placeholder.unparsed("name", name),
                        Placeholder.unparsed("seconds", String.valueOf(secondsLeft(sent.expiresAt())))));
            }
        });
    }

    /** Joins the party of {@code name} directly; a private party answers with the party-not-public text. */
    public void join(LocalPlayer player, String name) {
        run(player, locale -> {
            gateway.join(player.id().toString(), name);
            player.send(messages.message(locale, "command.join.done", Placeholder.unparsed("name", name)));
        });
    }

    /** The leader accepts the open request of {@code name}. */
    public void approve(LocalPlayer player, String name) {
        withParty(player, (party, locale) -> {
            gateway.acceptRequest(party.id(), player.id().toString(), name);
            player.send(messages.message(locale, "command.approve.done", Placeholder.unparsed("name", name)));
        });
    }

    /** The leader declines the open request of {@code name}. */
    public void reject(LocalPlayer player, String name) {
        withParty(player, (party, locale) -> {
            gateway.rejectRequest(party.id(), player.id().toString(), name);
            player.send(messages.message(locale, "command.reject.done", Placeholder.unparsed("name", name)));
        });
    }

    /** Whole seconds from now until {@code expiresAt}, rounded up, at least one. */
    private long secondsLeft(Instant expiresAt) {
        long millis = Duration.between(clock.instant(), expiresAt).toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }

    /**
     * The invite a command refers to. Sends the answer and returns empty when there is none to pick. An accept with no
     * matching invite from a player who is already in a party answers that they are already in it: that is what a
     * second click on an old [Accept] button means.
     */
    private Optional<Invite> chooseInvite(LocalPlayer player, Locale locale, String inviter, boolean accepting) {
        String playerId = player.id().toString();
        List<Invite> invites = gateway.invites(playerId);
        if (inviter == null && invites.size() > 1) {
            player.send(messages.message(locale, "command.invites.ambiguous",
                    Placeholder.unparsed("names", invites.stream().map(Invite::inviterName).collect(Collectors.joining(", ")))));
            return Optional.empty();
        }
        Optional<Invite> match = invites.stream()
                .filter(invite -> inviter == null || invite.inviterName().equalsIgnoreCase(inviter))
                .findFirst();
        if (match.isPresent()) {
            return match;
        }
        if (accepting && gateway.partyOf(playerId).isPresent()) {
            player.send(messages.message(locale, "command.accept.already-member"));
        } else if (invites.isEmpty()) {
            player.send(messages.message(locale, "command.invites.none"));
        } else {
            player.send(messages.message(locale, "command.invites.unknown", Placeholder.unparsed("name", inviter)));
        }
        return Optional.empty();
    }

    private void withParty(LocalPlayer player, PartyAction action) {
        run(player, locale -> {
            Optional<Party> party = gateway.partyOf(player.id().toString());
            if (party.isEmpty()) {
                player.send(messages.message(locale, "command.no-party"));
                return;
            }
            action.apply(party.get(), locale);
        });
    }

    private void withMember(LocalPlayer player, String name, MemberAction action) {
        run(player, locale -> {
            Optional<Party> party = gateway.partyOf(player.id().toString());
            if (party.isEmpty()) {
                player.send(messages.message(locale, "command.no-party"));
                return;
            }
            Optional<Member> target = party.get().memberNamed(name);
            if (target.isEmpty()) {
                player.send(messages.message(locale, "command.no-member", Placeholder.unparsed("name", name)));
                return;
            }
            action.apply(party.get(), target.get(), locale);
        });
    }

    private void run(LocalPlayer player, LocaleAction action) {
        executor.execute(() -> {
            Locale locale = player.locale();
            try {
                action.apply(locale);
            } catch (QueleaProblemException e) {
                player.send(messages.problem(locale, e));
                failures.failure(e, null);
            } catch (QueleaUnavailableException e) {
                logger.warn("Party command failed for {}: {}", player.name(), e.getMessage());
                player.send(messages.unavailable(locale));
                failures.failure(e, null);
            } catch (RuntimeException e) {
                logger.error("Party command failed unexpectedly for {}", player.name(), e);
                player.send(messages.unavailable(locale));
                failures.failure(e, null);
            }
        });
    }

    @FunctionalInterface
    private interface LocaleAction {
        void apply(Locale locale);
    }

    @FunctionalInterface
    private interface PartyAction {
        void apply(Party party, Locale locale);
    }

    @FunctionalInterface
    private interface MemberAction {
        void apply(Party party, Member target, Locale locale);
    }
}
