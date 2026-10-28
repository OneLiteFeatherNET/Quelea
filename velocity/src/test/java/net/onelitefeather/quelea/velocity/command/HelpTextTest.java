package net.onelitefeather.quelea.velocity.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.onelitefeather.quelea.velocity.i18n.Messages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelpTextTest {

    @TempDir
    Path overrides;

    private static void collect(Component component, List<ClickEvent> clicks, List<HoverEvent<?>> hovers) {
        if (component.clickEvent() != null) {
            clicks.add(component.clickEvent());
        }
        if (component.hoverEvent() != null) {
            hovers.add(component.hoverEvent());
        }
        for (Component child : component.children()) {
            collect(child, clicks, hovers);
        }
    }

    @Test
    void everyHelpLineSuggestsItsCommandOnClick() {
        Messages messages = Messages.load(overrides, LoggerFactory.getLogger(HelpTextTest.class));
        List<ClickEvent> clicks = new ArrayList<>();
        collect(HelpText.of(messages, Locale.ENGLISH), clicks, new ArrayList<>());

        List<String> suggestions = clicks.stream()
                .filter(click -> click.action() == ClickEvent.Action.SUGGEST_COMMAND)
                .map(click -> ((ClickEvent.Payload.Text) click.payload()).value())
                .toList();
        assertEquals(HelpText.ENTRIES.size(), suggestions.size(), "one clickable suggestion per help line");
        for (HelpText.Entry entry : HelpText.ENTRIES) {
            assertTrue(suggestions.contains(entry.suggestion()), "missing click for " + entry.name());
        }
        assertTrue(suggestions.contains("/party invite "), "the invite line suggests the command with a trailing space");
    }

    @Test
    void everyHelpLineHasHoverTextInTheLocale() {
        Messages messages = Messages.load(overrides, LoggerFactory.getLogger(HelpTextTest.class));
        List<HoverEvent<?>> hovers = new ArrayList<>();
        collect(HelpText.of(messages, Locale.GERMAN), new ArrayList<>(), hovers);

        assertEquals(HelpText.ENTRIES.size(), hovers.size(), "one hover per help line");
    }
}
