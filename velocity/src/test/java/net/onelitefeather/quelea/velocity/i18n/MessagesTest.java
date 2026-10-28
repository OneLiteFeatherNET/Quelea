package net.onelitefeather.quelea.velocity.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.onelitefeather.quelea.velocity.http.QueleaProblemException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagesTest {

    /** The problem codes of docs/problems.md, plus the plugin's own unavailable message. */
    private static final List<String> PROBLEM_CODES = List.of(
            "validation-failed", "not-found", "method-not-allowed", "party-not-found", "not-in-party",
            "already-in-party", "not-leader", "not-member", "party-full", "player-not-online", "invite-not-found",
            "cannot-target-self", "lock-timeout", "internal-error");

    private final Messages messages = Messages.load();

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void bothLanguagesDefineTheSameKeys() {
        Set<String> english = new TreeSet<>(messages.keys(Locale.ENGLISH));
        Set<String> german = new TreeSet<>(messages.keys(Locale.GERMAN));

        assertEquals(english, german, "every key must exist in messages_en and messages_de");
    }

    @Test
    void everyProblemCodeHasAMessageInBothLanguages() {
        for (String code : PROBLEM_CODES) {
            String key = "problem." + code;
            assertTrue(messages.hasKey(Locale.ENGLISH, key), "English is missing " + key);
            assertTrue(messages.hasKey(Locale.GERMAN, key), "German is missing " + key);
        }
    }

    @Test
    void unavailableHasAMessageInBothLanguages() {
        assertTrue(messages.hasKey(Locale.ENGLISH, "problem.unavailable"));
        assertTrue(messages.hasKey(Locale.GERMAN, "problem.unavailable"));
    }

    @Test
    void germanClientsGetGermanText() {
        String text = plain(messages.problem(Locale.GERMAN, new QueleaProblemException("not-leader", 403, null)));

        assertTrue(text.contains("Leiter"), "German text: " + text);
        assertFalse(text.contains("leader"), "no English text: " + text);
    }

    @Test
    void otherLanguagesGetEnglishText() {
        String text = plain(messages.problem(Locale.FRENCH, new QueleaProblemException("not-leader", 403, null)));

        assertTrue(text.contains("leader"), "English fallback: " + text);
    }

    @Test
    void unknownProblemCodeFallsBackToAGenericMessage() {
        String text = plain(messages.problem(Locale.ENGLISH, new QueleaProblemException("brand-new-code", 409, null)));

        assertTrue(text.contains("brand-new-code"), "the code is shown for support: " + text);
    }

    @Test
    void playerTextIsNeverReadAsMiniMessage() {
        Component line = messages.line(Locale.ENGLISH, "party.chat",
                Placeholder.unparsed("sender", "Alice"),
                Placeholder.unparsed("message", "<red>not red</red>"));

        assertTrue(plain(line).endsWith("<red>not red</red>"), plain(line));
    }
}
