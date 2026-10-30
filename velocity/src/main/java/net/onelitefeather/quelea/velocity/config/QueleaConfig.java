package net.onelitefeather.quelea.velocity.config;

import net.onelitefeather.quelea.velocity.auth.ClientCredentials;
import net.onelitefeather.quelea.velocity.follow.FollowMode;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Plugin settings. Read from {@code plugins/quelea/config.properties}; an environment variable of the same meaning
 * (see the constants) overrides the file. Unset values fall back to the defaults.
 *
 * @param url         base URL of the party service
 * @param proxyId     this proxy's id, a random UUID per start when not configured
 * @param lobbyPattern regex that marks lobby server names
 * @param followMode  which members follow a warp
 * @param credentials the app registration used to get tokens; incomplete values mean the plugin sends no token
 * @param sentryDsn   the Sentry DSN; {@code null} switches error reporting off. Never logged.
 * @param sentryEnvironment the environment tag of reported errors
 */
public record QueleaConfig(String url, String proxyId, String lobbyPattern, FollowMode followMode,
                           ClientCredentials credentials, String sentryDsn, String sentryEnvironment) {

    public static final String FILE_NAME = "config.properties";

    public static final String ENV_URL = "QUELEA_URL";
    public static final String ENV_PROXY_ID = "QUELEA_PROXY_ID";
    public static final String ENV_LOBBY_PATTERN = "QUELEA_LOBBY_PATTERN";
    public static final String ENV_FOLLOW_MODE = "QUELEA_FOLLOW_MODE";
    public static final String ENV_TENANT_ID = "QUELEA_TENANT_ID";
    public static final String ENV_CLIENT_ID = "QUELEA_CLIENT_ID";
    public static final String ENV_CLIENT_SECRET = "QUELEA_CLIENT_SECRET";
    public static final String ENV_AUTH_SCOPE = "QUELEA_AUTH_SCOPE";
    public static final String ENV_SENTRY_DSN = "QUELEA_SENTRY_DSN";
    public static final String ENV_SENTRY_ENVIRONMENT = "QUELEA_SENTRY_ENVIRONMENT";

    public static final String DEFAULT_URL = "http://localhost:8080";
    public static final String DEFAULT_LOBBY_PATTERN = "(?i)^lobby.*";
    public static final String DEFAULT_FOLLOW_MODE = "lobby";
    public static final String DEFAULT_SENTRY_ENVIRONMENT = "production";

    public QueleaConfig {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        if (proxyId == null || proxyId.isBlank()) {
            throw new IllegalArgumentException("proxyId must not be blank");
        }
        Pattern.compile(lobbyPattern);
        if (followMode == null) {
            throw new IllegalArgumentException("followMode must be set");
        }
        if (credentials == null) {
            throw new IllegalArgumentException("credentials must be set");
        }
        if (sentryEnvironment == null || sentryEnvironment.isBlank()) {
            throw new IllegalArgumentException("sentryEnvironment must not be blank");
        }
    }

    /**
     * Loads the configuration. On the first start the file is created with commented defaults.
     *
     * @param file the config file, usually {@code <data directory>/config.properties}
     * @param env  environment lookup; {@code System::getenv} in production, a map in tests
     * @return the effective configuration
     * @throws IOException              when the file cannot be read or written
     * @throws IllegalArgumentException for an invalid value (bad follow mode, bad regex)
     */
    public static QueleaConfig load(Path file, Function<String, String> env) throws IOException {
        if (!Files.exists(file)) {
            writeDefaults(file);
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        String proxyId = pick(env, ENV_PROXY_ID, properties, "proxy-id", null);
        return new QueleaConfig(
                pick(env, ENV_URL, properties, "url", DEFAULT_URL),
                proxyId == null ? UUID.randomUUID().toString() : proxyId,
                pick(env, ENV_LOBBY_PATTERN, properties, "lobby-pattern", DEFAULT_LOBBY_PATTERN),
                FollowMode.parse(pick(env, ENV_FOLLOW_MODE, properties, "follow-mode", DEFAULT_FOLLOW_MODE)),
                new ClientCredentials(
                        pick(env, ENV_TENANT_ID, properties, "tenant-id", null),
                        pick(env, ENV_CLIENT_ID, properties, "client-id", null),
                        pick(env, ENV_CLIENT_SECRET, properties, "client-secret", null),
                        pick(env, ENV_AUTH_SCOPE, properties, "auth-scope", null)),
                pick(env, ENV_SENTRY_DSN, properties, "sentry-dsn", null),
                pick(env, ENV_SENTRY_ENVIRONMENT, properties, "sentry-environment", DEFAULT_SENTRY_ENVIRONMENT));
    }

    private static String pick(Function<String, String> env, String envKey, Properties properties, String key,
                               String fallback) {
        String fromEnv = env.apply(envKey);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String fromFile = properties.getProperty(key);
        if (fromFile != null && !fromFile.isBlank()) {
            return fromFile.trim();
        }
        return fallback;
    }

    private static void writeDefaults(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("""
                    # Quelea party plugin.
                    # Every value below can also be set by an environment variable; the variable wins.
                    # Uncommented lines override the defaults shown in the comments.

                    # Base URL of the party service. QUELEA_URL
                    #url=http://localhost:8080

                    # Name of this proxy in the party service. Blank: a random id per start. QUELEA_PROXY_ID
                    #proxy-id=

                    # Regex matching lobby server names; moves to other servers warp the party. QUELEA_LOBBY_PATTERN
                    #lobby-pattern=(?i)^lobby.*

                    # Who follows a warp: lobby (only members in a lobby) or always. QUELEA_FOLLOW_MODE
                    #follow-mode=lobby

                    # Sentry DSN for error reports. Blank or unset: no errors are reported and Sentry is not contacted.
                    # Prefer the environment for it. QUELEA_SENTRY_DSN
                    #sentry-dsn=

                    # Environment tag of the reported errors. QUELEA_SENTRY_ENVIRONMENT
                    #sentry-environment=production

                    # Entra ID app registration of this plugin (see docs/entra-setup.md). Without all four values the
                    # plugin sends no token and only a dev backend accepts its requests. Prefer the environment
                    # for client-secret.

                    # Entra tenant id (GUID or domain). QUELEA_TENANT_ID
                    #tenant-id=

                    # Client id of the plugin's app registration. QUELEA_CLIENT_ID
                    #client-id=

                    # Secret of that app registration. QUELEA_CLIENT_SECRET
                    #client-secret=

                    # Scope of the token, api://<client id of the Quelea API>/.default. QUELEA_AUTH_SCOPE
                    #auth-scope=
                    """);
        }
    }
}
