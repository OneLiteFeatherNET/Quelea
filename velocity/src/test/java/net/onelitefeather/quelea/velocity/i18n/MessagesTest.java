package net.onelitefeather.quelea.velocity.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.onelitefeather.quelea.velocity.http.QueleaProblemException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagesTest {

    /** The problem codes of docs/problems.md, plus the plugin's own unavailable message. */
    private static final List<String> PROBLEM_CODES = List.of(
            "validation-failed", "not-found", "method-not-allowed", "unsupported-media-type", "unauthorized", "forbidden",
            "precondition-failed", "idempotency-key-reused", "request-in-progress", "party-not-found", "not-in-party",
            "already-in-party", "not-leader", "not-member", "party-full", "player-not-online", "invite-not-found",
            "cannot-target-self", "lock-timeout", "internal-error");

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(MessagesTest.class);

    @TempDir
    Path overrides;

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static Properties builtIn(String language) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = MessagesTest.class.getResourceAsStream("/lang/messages_" + language + ".properties")) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    private void write(String fileName, String content) throws IOException {
        Files.writeString(overrides.resolve(fileName), content, StandardCharsets.UTF_8);
    }

    private Messages messages() {
        return Messages.load(overrides, LOG);
    }

    @Test
    void everyProblemCodeHasAMessageInBothLanguages() {
        Messages messages = messages();
        for (String code : PROBLEM_CODES) {
            String key = "problem." + code;
            assertTrue(messages.hasKey(Locale.ENGLISH, key), "English is missing " + key);
            assertTrue(messages.hasKey(Locale.GERMAN, key), "German is missing " + key);
        }
    }

    @Test
    void unavailableHasAMessageInBothLanguages() {
        assertTrue(messages().hasKey(Locale.ENGLISH, "problem.unavailable"));
        assertTrue(messages().hasKey(Locale.GERMAN, "problem.unavailable"));
    }

    @Test
    void germanClientsGetGermanText() {
        String text = plain(messages().problem(Locale.GERMAN, new QueleaProblemException("not-leader", 403, null)));

        assertTrue(text.contains("Leiter"), "German text: " + text);
        assertFalse(text.contains("leader"), "no English text: " + text);
    }

    @Test
    void germanRegionalClientGetsGermanText() {
        String text = plain(messages().problem(Locale.GERMANY, new QueleaProblemException("not-leader", 403, null)));

        assertTrue(text.contains("Leiter"), "de_DE resolves to de: " + text);
    }

    @Test
    void unknownLanguageGetsEnglishText() {
        String text = plain(messages().problem(Locale.FRENCH, new QueleaProblemException("not-leader", 403, null)));

        assertTrue(text.contains("leader"), "English fallback: " + text);
    }

    @Test
    void overrideFileAddsALanguageAndMissingKeysFallBackToEnglish() throws IOException {
        write("messages_fr.properties", "party.joined=<white><player></white> a rejoint la party.\n");

        Messages messages = messages();

        assertEquals("Bob a rejoint la party.", plain(messages.line(Locale.FRANCE, "party.joined",
                Placeholder.unparsed("player", "Bob"))));
        assertEquals("Bob left the party.", plain(messages.line(Locale.FRANCE, "party.left",
                Placeholder.unparsed("player", "Bob"))), "a key the override does not define is English");
    }

    @Test
    void overrideFileRecoloursOneKeyOfABuiltInLanguage() throws IOException {
        write("messages_de.properties", "prefix=<green>Party</green> <dark_gray>»</dark_gray>\n");

        Messages messages = messages();
        String joined = plain(messages.message(Locale.GERMAN, "command.accept.done"));

        assertTrue(joined.startsWith("Party »"), joined);
        assertTrue(joined.contains("Du bist der Party beigetreten."), "other German texts stay: " + joined);
    }

    @Test
    void brokenOverrideTemplateFallsBackToTheEnglishText() throws IOException {
        write("messages_de.properties", "party.joined=<rainbow:-1>broken\n");

        String text = plain(messages().line(Locale.GERMAN, "party.joined", Placeholder.unparsed("player", "Bob")));

        assertEquals("Bob joined the party.", text);
    }

    @Test
    void unknownProblemCodeFallsBackToAGenericMessage() {
        String text = plain(messages().problem(Locale.ENGLISH, new QueleaProblemException("brand-new-code", 409, null)));

        assertTrue(text.contains("brand-new-code"), "the code is shown for support: " + text);
    }

    @Test
    void expiryMessagesExistInBothLanguages() {
        for (String key : List.of("party.expiring", "party.disbanded.expired-alone")) {
            assertTrue(messages().hasKey(Locale.ENGLISH, key), "English is missing " + key);
            assertTrue(messages().hasKey(Locale.GERMAN, key), "German is missing " + key);
        }
    }

    @Test
    void germanExpiryMessageNamesTheMinutesLeft() {
        String text = plain(messages().line(Locale.GERMAN, "party.expiring", Placeholder.unparsed("minutes", "5")));

        assertTrue(text.contains("5 Minuten"), "German text: " + text);
    }

    @Test
    void playerTextIsNeverReadAsMiniMessage() {
        Component line = messages().line(Locale.ENGLISH, "party.chat",
                Placeholder.unparsed("sender", "Alice"),
                Placeholder.unparsed("message", "<red>not red</red>"));

        assertTrue(plain(line).endsWith("<red>not red</red>"), plain(line));
    }

    @Test
    void bothBuiltInBundlesDefineExactlyTheEnglishKeys() throws IOException {
        Set<String> english = new TreeSet<>(builtIn("en").stringPropertyNames());
        Set<String> german = new TreeSet<>(builtIn("de").stringPropertyNames());

        assertEquals(english, german, "every key must exist in messages_en and messages_de");
    }

    @Test
    void everyBuiltInTemplateIsValidMiniMessage() throws IOException {
        MiniMessage miniMessage = MiniMessage.miniMessage();
        for (String language : List.of("en", "de")) {
            Properties bundle = builtIn(language);
            for (String key : bundle.stringPropertyNames()) {
                String template = bundle.getProperty(key);
                assertTrue(parses(miniMessage, template), language + " " + key + " does not parse: " + template);
            }
        }
    }

    private static boolean parses(MiniMessage miniMessage, String template) {
        try {
            miniMessage.deserialize(template);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
