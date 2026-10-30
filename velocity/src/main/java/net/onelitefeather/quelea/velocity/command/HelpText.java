package net.onelitefeather.quelea.velocity.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.onelitefeather.quelea.velocity.i18n.Messages;

import java.util.List;
import java.util.Locale;

/**
 * The {@code /party} help: a title, a separator and one line per command. Clicking a line suggests its command in
 * the chat input; hovering explains it in the player's language ({@code command.help.<name>.hover}).
 */
final class HelpText {

    /** A help line: the bundle name, and the command the click suggests. */
    record Entry(String name, String suggestion) {
    }

    static final List<Entry> ENTRIES = List.of(
            new Entry("create", "/party create"),
            new Entry("invite", "/party invite "),
            new Entry("accept", "/party accept "),
            new Entry("deny", "/party deny "),
            new Entry("public", "/party public "),
            new Entry("request", "/party request "),
            new Entry("join", "/party join "),
            new Entry("approve", "/party approve "),
            new Entry("reject", "/party reject "),
            new Entry("leave", "/party leave"),
            new Entry("list", "/party list"),
            new Entry("kick", "/party kick "),
            new Entry("promote", "/party promote "),
            new Entry("disband", "/party disband"),
            new Entry("warp", "/party warp"),
            new Entry("chat", "/pc "));

    private HelpText() {
    }

    static Component of(Messages messages, Locale locale) {
        Component help = messages.message(locale, "command.help.title")
                .append(Component.newline())
                .append(messages.line(locale, "command.help.separator"));
        for (Entry entry : ENTRIES) {
            help = help.append(Component.newline()).append(line(messages, locale, entry));
        }
        return help;
    }

    private static Component line(Messages messages, Locale locale, Entry entry) {
        return messages.line(locale, "command.help." + entry.name())
                .clickEvent(ClickEvent.suggestCommand(entry.suggestion()))
                .hoverEvent(HoverEvent.showText(messages.line(locale, "command.help." + entry.name() + ".hover")));
    }
}
