package net.onelitefeather.quelea.velocity.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.onelitefeather.quelea.velocity.http.QueleaProblemException;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Renders the plugin's messages. The bundle for a player is looked up as {@code language_COUNTRY}, then
 * {@code language}, then English. Built-in bundles come from the classpath ({@code en}, {@code de}); every
 * {@code messages_<tag>.properties} in the override directory is merged key by key over the bundle of the same tag,
 * or forms a new language. A key missing from the bundles falls back to the built-in English text. Templates are
 * MiniMessage strings; an override template that does not parse is logged once and replaced by the English text.
 */
public final class Messages {

    private static final String PROBLEM_PREFIX = "problem.";
    private static final String UNKNOWN_PROBLEM = "problem.unknown";
    private static final String ENGLISH = "en";
    private static final List<String> BUILT_IN = List.of(ENGLISH, "de");
    private static final String FILE_PREFIX = "messages_";
    private static final String FILE_SUFFIX = ".properties";

    /** Merged bundles by normalised tag ({@code en}, {@code de}, {@code fr}, {@code de_AT}). */
    private final Map<String, Properties> bundles;
    /** The built-in English bundle, the last resort for a key that an override broke. */
    private final Properties builtInEnglish;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private Messages(Map<String, Properties> bundles, Properties builtInEnglish) {
        this.bundles = bundles;
        this.builtInEnglish = builtInEnglish;
    }

    /**
     * Reads the built-in bundles from the classpath and merges the overrides from {@code overrideDirectory}.
     *
     * @param overrideDirectory the directory with {@code messages_<tag>.properties} files; a missing directory means
     *                          no overrides, {@code null} too
     * @param logger            receives one warning per override template that does not parse
     * @return the messages, read once
     */
    public static Messages load(Path overrideDirectory, Logger logger) {
        Properties builtInEnglish = readBuiltIn(ENGLISH);
        Map<String, Properties> bundles = new HashMap<>();
        bundles.put(ENGLISH, copy(builtInEnglish));
        for (String language : BUILT_IN) {
            if (!ENGLISH.equals(language)) {
                bundles.put(language, readBuiltIn(language));
            }
        }
        if (overrideDirectory != null && Files.isDirectory(overrideDirectory)) {
            for (Path file : overrideFiles(overrideDirectory)) {
                String tag = tagOf(file.getFileName().toString());
                Properties override = readFile(file, logger);
                if (tag != null && override != null) {
                    merge(bundles, tag, override, builtInEnglish, logger);
                }
            }
        }
        return new Messages(bundles, builtInEnglish);
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
     * @return whether {@code key} has a template for {@code locale}, directly or through the English fallback
     */
    public boolean hasKey(Locale locale, String key) {
        return lookup(locale, key) != null;
    }

    private String template(Locale locale, String key) {
        String template = lookup(locale, key);
        if (template == null) {
            throw new IllegalArgumentException("no message for key " + key);
        }
        return template;
    }

    /**
     * The first template for {@code key} along the chain {@code language_COUNTRY}, {@code language}, English, or
     * {@code null}.
     */
    private String lookup(Locale locale, String key) {
        for (Properties bundle : chain(locale)) {
            String template = bundle.getProperty(key);
            if (template != null) {
                return template;
            }
        }
        return null;
    }

    private List<Properties> chain(Locale locale) {
        List<Properties> chain = new ArrayList<>(4);
        if (locale != null) {
            String language = locale.getLanguage();
            String country = locale.getCountry();
            if (!country.isEmpty() && bundles.containsKey(language + "_" + country)) {
                chain.add(bundles.get(language + "_" + country));
            }
            if (bundles.containsKey(language)) {
                chain.add(bundles.get(language));
            }
        }
        chain.add(bundles.get(ENGLISH));
        chain.add(builtInEnglish);
        return chain;
    }

    private static void merge(Map<String, Properties> bundles, String tag, Properties override,
                              Properties builtInEnglish, Logger logger) {
        Properties target = bundles.computeIfAbsent(tag, ignored -> new Properties());
        for (String key : override.stringPropertyNames()) {
            String template = override.getProperty(key);
            if (parses(template)) {
                target.setProperty(key, template);
            } else {
                // Dropping the key lets the lookup fall through to the English text.
                target.remove(key);
                logger.warn("Message {} for language {} is not valid MiniMessage; the English text is used",
                        key, tag);
            }
        }
    }

    private static boolean parses(String template) {
        try {
            MiniMessage.miniMessage().deserialize(template);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static List<Path> overrideFiles(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> tagOf(path.getFileName().toString()) != null)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @return the normalised tag of {@code messages_<tag>.properties} ({@code fr} or {@code de_AT}), or {@code null}
     * for any other file name
     */
    static String tagOf(String fileName) {
        if (!fileName.startsWith(FILE_PREFIX) || !fileName.endsWith(FILE_SUFFIX)) {
            return null;
        }
        String raw = fileName.substring(FILE_PREFIX.length(), fileName.length() - FILE_SUFFIX.length());
        String[] parts = raw.split("_", -1);
        if (parts.length > 2 || parts[0].isEmpty() || !parts[0].chars().allMatch(Character::isLetter)) {
            return null;
        }
        if (parts.length == 1) {
            return parts[0].toLowerCase(Locale.ROOT);
        }
        if (parts[1].length() != 2 || !parts[1].chars().allMatch(Character::isLetter)) {
            return null;
        }
        return parts[0].toLowerCase(Locale.ROOT) + "_" + parts[1].toUpperCase(Locale.ROOT);
    }

    private static Properties readBuiltIn(String language) {
        String path = "/lang/" + FILE_PREFIX + language + FILE_SUFFIX;
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

    private static Properties readFile(Path file, Logger logger) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (IOException e) {
            logger.warn("Message file {} could not be read and is ignored: {}", file, e.getMessage());
            return null;
        }
    }

    private static Properties copy(Properties source) {
        Properties copy = new Properties();
        copy.putAll(source);
        return copy;
    }
}
