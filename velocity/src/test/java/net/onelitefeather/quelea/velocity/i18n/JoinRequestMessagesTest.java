package net.onelitefeather.quelea.velocity.i18n;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.onelitefeather.quelea.velocity.http.QueleaProblemException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The join-request problem keys and the cooldown wording. */
class JoinRequestMessagesTest {

    private static final List<String> JOIN_CODES = List.of(
            "party-not-public", "party-banned", "target-not-in-party", "join-request-pending",
            "join-request-cooldown", "join-request-not-found");

    @TempDir
    Path overrides;

    private final Messages messages = Messages.load(overrides, LoggerFactory.getLogger(JoinRequestMessagesTest.class));

    private static Properties builtIn(String language) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = JoinRequestMessagesTest.class.getResourceAsStream("/lang/messages_" + language + ".properties")) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    private static String plain(net.kyori.adventure.text.Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void everyJoinProblemHasAKeyInEnglishAndGerman() throws IOException {
        Properties english = builtIn("en");
        Properties german = builtIn("de");

        for (String code : JOIN_CODES) {
            assertTrue(english.containsKey("problem." + code), "English is missing problem." + code);
            assertTrue(german.containsKey("problem." + code), "German is missing problem." + code);
        }
    }

    @Test
    void theCooldownNamesTheWaitWhenTheServiceSentRetryAfter() {
        String text = plain(messages.problem(Locale.ENGLISH,
                new QueleaProblemException("join-request-cooldown", 429, null, 42L)));

        assertTrue(text.contains("42 seconds"), text);
    }

    @Test
    void theCooldownNamesNoNumberWithoutRetryAfter() {
        String text = plain(messages.problem(Locale.ENGLISH,
                new QueleaProblemException("join-request-cooldown", 429, null)));

        assertFalse(text.matches(".*\\d.*"), "without Retry-After the text must not invent a number: " + text);
    }
}
