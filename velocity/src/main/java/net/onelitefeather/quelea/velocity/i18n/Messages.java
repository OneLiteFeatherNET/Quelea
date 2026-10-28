package net.onelitefeather.quelea.velocity.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.onelitefeather.quelea.velocity.http.QueleaProblemException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Renders the plugin's messages. German for players whose client locale is German, English otherwise. Templates are
 * MiniMessage strings from {@code lang/messages_en.properties} and {@code lang/messages_de.properties}.
 */
public final class Messages {

    private static final String PROBLEM_PREFIX = "problem.";
    private static final String UNKNOWN_PROBLEM = "problem.unknown";

    private final Properties english;
    private final Properties german;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private Messages(Properties english, Properties german) {
        this.english = english;
        this.german = german;
    }

    /**
     * @return the messages read from the plugin's classpath bundles
     */
    public static Messages load() {
        return new Messages(read("en"), read("de"));
    }

    private static Properties read(String language) {
        String path = "/lang/messages_" + language + ".properties";
        try (InputStream in = Messages.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing message bundle " + path);
            }
            Properties properties = new Properties();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @param locale the player's locale
     * @return {@code true} for German, {@code false} for English
     */
    public static boolean isGerman(Locale locale) {
        return locale != null && "de".equals(locale.getLanguage());
    }

    /**
     * A message with the plugin prefix.
     */
    public Component message(Locale locale, String key, TagResolver... resolvers) {
        return line(locale, "prefix")
                .append(Component.space())
                .append(line(locale, key, resolvers));
    }

    /**
     * A message without the prefix, for chat lines and list entries.
     */
    public Component line(Locale locale, String key, TagResolver... resolvers) {
        return miniMessage.deserialize(template(locale, key), resolvers);
    }

    /**
     * The message for a problem document; unknown codes fall back to a generic message.
     */
    public Component problem(Locale locale, QueleaProblemException problem) {
        String key = PROBLEM_PREFIX + problem.code();
        if (hasKey(locale, key)) {
            return message(locale, key);
        }
        return message(locale, UNKNOWN_PROBLEM, Placeholder.unparsed("code", problem.code()));
    }

    /**
     * @return the message for an unreachable party service
     */
    public Component unavailable(Locale locale) {
        return message(locale, "problem.unavailable");
    }

    /**
     * @return every message key of the bundle used for {@code locale}
     */
    public Set<String> keys(Locale locale) {
        return bundle(locale).stringPropertyNames();
    }

    /**
     * @return whether the bundle for {@code locale} defines {@code key}
     */
    public boolean hasKey(Locale locale, String key) {
        return bundle(locale).containsKey(key);
    }

    private Properties bundle(Locale locale) {
        return isGerman(locale) ? german : english;
    }

    private String template(Locale locale, String key) {
        String template = bundle(locale).getProperty(key);
        if (template == null) {
            template = english.getProperty(key);
        }
        if (template == null) {
            throw new IllegalArgumentException("no message for key " + key);
        }
        return template;
    }
}
