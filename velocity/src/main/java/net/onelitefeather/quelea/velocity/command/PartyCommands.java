package net.onelitefeather.quelea.velocity.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.onelitefeather.quelea.velocity.i18n.Messages;
import net.onelitefeather.quelea.velocity.player.LocalPlayer;
import net.onelitefeather.quelea.velocity.player.LocalPlayers;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The Brigadier trees of {@code /party} and {@code /pc}. The trees only map input to {@link PartyActions} calls.
 */
public final class PartyCommands {

    private static final String PLAYER = "player";
    private static final String MESSAGE = "message";

    private final PartyActions actions;
    private final LocalPlayers players;
    private final Function<Player, LocalPlayer> wrap;
    private final Messages messages;

    /**
     * @param wrap turns a Velocity player into the seam type; the proxy's {@code VelocityLocalPlayers#wrap}
     */
    public PartyCommands(PartyActions actions, LocalPlayers players, Function<Player, LocalPlayer> wrap,
                         Messages messages) {
        this.actions = actions;
        this.players = players;
        this.wrap = wrap;
        this.messages = messages;
    }

    /**
     * @return the {@code /party} command; register it with the alias {@code p}
     */
    public BrigadierCommand party() {
        LiteralArgumentBuilder<CommandSource> root = BrigadierCommand.literalArgumentBuilder("party")
                .executes(context -> {
                    context.getSource().sendMessage(help(context.getSource()));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand.literalArgumentBuilder("create")
                        .executes(context -> playersOnly(context.getSource(), actions::create)))
                .then(BrigadierCommand.literalArgumentBuilder("invite")
                        .then(playerArgument()
                                .executes(context -> playersOnly(context.getSource(),
                                        player -> actions.invite(player, playerName(context))))))
                .then(BrigadierCommand.literalArgumentBuilder("accept")
                        .executes(context -> playersOnly(context.getSource(), player -> actions.accept(player, null)))
                        .then(playerArgument()
                                .executes(context -> playersOnly(context.getSource(),
                                        player -> actions.accept(player, playerName(context))))))
                .then(BrigadierCommand.literalArgumentBuilder("deny")
                        .executes(context -> playersOnly(context.getSource(), player -> actions.deny(player, null)))
                        .then(playerArgument()
                                .executes(context -> playersOnly(context.getSource(),
                                        player -> actions.deny(player, playerName(context))))))
                .then(BrigadierCommand.literalArgumentBuilder("leave")
                        .executes(context -> playersOnly(context.getSource(), actions::leave)))
                .then(BrigadierCommand.literalArgumentBuilder("kick")
                        .then(playerArgument()
                                .executes(context -> playersOnly(context.getSource(),
                                        player -> actions.kick(player, playerName(context))))))
                .then(BrigadierCommand.literalArgumentBuilder("promote")
                        .then(playerArgument()
                                .executes(context -> playersOnly(context.getSource(),
                                        player -> actions.promote(player, playerName(context))))))
                .then(BrigadierCommand.literalArgumentBuilder("disband")
                        .executes(context -> playersOnly(context.getSource(), actions::disband)))
                .then(BrigadierCommand.literalArgumentBuilder("list")
                        .executes(context -> playersOnly(context.getSource(), actions::list)))
                .then(BrigadierCommand.literalArgumentBuilder("warp")
                        .executes(context -> playersOnly(context.getSource(), actions::warp)))
                .then(BrigadierCommand.literalArgumentBuilder("public")
                        .then(BrigadierCommand.literalArgumentBuilder("on")
                                .executes(context -> playersOnly(context.getSource(), actions::publicOn)))
                        .then(BrigadierCommand.literalArgumentBuilder("off")
                                .executes(context -> playersOnly(context.getSource(), actions::publicOff))))
                .then(BrigadierCommand.literalArgumentBuilder("request")
                        .then(playerArgument()
                                .executes(context -> playersOnly(context.getSource(),
                                        player -> actions.request(player, playerName(context))))))
                .then(BrigadierCommand.literalArgumentBuilder("join")
                        .then(playerArgument()
                                .executes(context -> playersOnly(context.getSource(),
                                        player -> actions.join(player, playerName(context))))))
                .then(BrigadierCommand.literalArgumentBuilder("approve")
                        .then(playerArgument()
                                .executes(context -> playersOnly(context.getSource(),
                                        player -> actions.approve(player, playerName(context))))))
                .then(BrigadierCommand.literalArgumentBuilder("reject")
                        .then(playerArgument()
                                .executes(context -> playersOnly(context.getSource(),
                                        player -> actions.reject(player, playerName(context))))));
        return new BrigadierCommand(root);
    }

    /**
     * @return the {@code /pc <message>} command
     */
    public BrigadierCommand chat() {
        LiteralArgumentBuilder<CommandSource> root = BrigadierCommand.literalArgumentBuilder("pc")
                .then(BrigadierCommand.requiredArgumentBuilder(MESSAGE, StringArgumentType.greedyString())
                        .executes(context -> playersOnly(context.getSource(),
                                player -> actions.chat(player, StringArgumentType.getString(context, MESSAGE)))));
        return new BrigadierCommand(root);
    }

    private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSource, String> playerArgument() {
        return BrigadierCommand.requiredArgumentBuilder(PLAYER, StringArgumentType.word())
                .suggests(onlinePlayerNames());
    }

    private static String playerName(com.mojang.brigadier.context.CommandContext<CommandSource> context) {
        return StringArgumentType.getString(context, PLAYER);
    }

    private SuggestionProvider<CommandSource> onlinePlayerNames() {
        return (context, builder) -> {
            String remaining = builder.getRemainingLowerCase();
            players.all().stream()
                    .map(LocalPlayer::name)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(remaining))
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    private int playersOnly(CommandSource source, Consumer<LocalPlayer> action) {
        if (source instanceof Player player) {
            action.accept(wrap.apply(player));
            return Command.SINGLE_SUCCESS;
        }
        source.sendMessage(messages.message(Locale.ENGLISH, "command.players-only"));
        return 0;
    }

    private net.kyori.adventure.text.Component help(CommandSource source) {
        Locale locale = source instanceof Player player ? player.getEffectiveLocale() : Locale.ENGLISH;
        return HelpText.of(messages, locale);
    }
}
