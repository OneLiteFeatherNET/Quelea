package net.onelitefeather.quelea.velocity.config;

import net.onelitefeather.quelea.velocity.follow.FollowMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueleaConfigTest {

    @TempDir
    Path dataDirectory;

    private Path file() {
        return dataDirectory.resolve("config.properties");
    }

    private void writeFile(String content) throws IOException {
        Files.writeString(file(), content, StandardCharsets.UTF_8);
    }

    @Test
    void usesTheDefaultsWhenNothingIsConfigured() throws IOException {
        QueleaConfig config = QueleaConfig.load(file(), name -> null);

        assertEquals("http://localhost:8080", config.url());
        assertEquals("(?i)^lobby.*", config.lobbyPattern());
        assertEquals(FollowMode.LOBBY, config.followMode());
    }

    @Test
    void sentryIsOffWithoutADsnAndTheEnvironmentDefaultsToProduction() throws IOException {
        QueleaConfig config = QueleaConfig.load(file(), name -> null);

        assertNull(config.sentryDsn(), "no DSN means no error reporting");
        assertEquals("production", config.sentryEnvironment());
    }

    @Test
    void sentryKeysAreReadFromTheEnvironmentBeforeTheFile() throws IOException {
        writeFile("sentry-dsn=https://from-file@sentry.example/1\nsentry-environment=dev\n");

        QueleaConfig config = QueleaConfig.load(file(), Map.of(
                "QUELEA_SENTRY_DSN", "https://from-env@sentry.example/2",
                "QUELEA_SENTRY_ENVIRONMENT", "staging")::get);

        assertEquals("https://from-env@sentry.example/2", config.sentryDsn());
        assertEquals("staging", config.sentryEnvironment());
    }

    @Test
    void sentryKeysAreReadFromTheFileWhenTheEnvironmentIsSilent() throws IOException {
        writeFile("sentry-dsn=https://from-file@sentry.example/1\nsentry-environment=dev\n");

        QueleaConfig config = QueleaConfig.load(file(), name -> null);

        assertEquals("https://from-file@sentry.example/1", config.sentryDsn());
        assertEquals("dev", config.sentryEnvironment());
    }

    @Test
    void generatesARandomProxyIdWhenNoneIsConfigured() throws IOException {
        QueleaConfig first = QueleaConfig.load(file(), name -> null);
        QueleaConfig second = QueleaConfig.load(file(), name -> null);

        assertTrue(first.proxyId() != null && !first.proxyId().isBlank());
        assertEquals(first.proxyId(), UUID.fromString(first.proxyId()).toString());
        assertNotEquals(first.proxyId(), second.proxyId(), "each start gets its own id");
    }

    @Test
    void environmentOverridesTheFile() throws IOException {
        writeFile("url=http://from-file\nfollow-mode=always\n");

        QueleaConfig config = QueleaConfig.load(file(), Map.of("QUELEA_URL", "http://from-env")::get);

        assertEquals("http://from-env", config.url());
        assertEquals(FollowMode.ALWAYS, config.followMode(), "keys the environment does not set keep the file value");
    }

    @Test
    void fileValuesAreUsedWhenTheEnvironmentIsSilent() throws IOException {
        writeFile("proxy-id=lobby-proxy-1\nlobby-pattern=^Hub.*\n");

        QueleaConfig config = QueleaConfig.load(file(), name -> null);

        assertEquals("lobby-proxy-1", config.proxyId());
        assertEquals("^Hub.*", config.lobbyPattern());
    }

    @Test
    void blankEnvironmentValuesDoNotOverride() throws IOException {
        writeFile("url=http://from-file\n");

        QueleaConfig config = QueleaConfig.load(file(), Map.of("QUELEA_URL", "   ")::get);

        assertEquals("http://from-file", config.url());
    }

    @Test
    void writesACommentedDefaultFileOnFirstStart() throws IOException {
        QueleaConfig.load(file(), name -> null);

        String written = Files.readString(file(), StandardCharsets.UTF_8);
        assertTrue(written.contains("QUELEA_URL"), "the file names the environment variables");
        assertTrue(written.contains("#url=http://localhost:8080"), "the defaults are written as comments");
    }

    @Test
    void keepsAnExistingFileUntouched() throws IOException {
        writeFile("url=http://kept\n");

        QueleaConfig.load(file(), name -> null);

        assertEquals("url=http://kept\n", Files.readString(file(), StandardCharsets.UTF_8));
    }

    @Test
    void rejectsAnUnknownFollowMode() throws IOException {
        assertThrows(IllegalArgumentException.class,
                () -> QueleaConfig.load(file(), Map.of("QUELEA_FOLLOW_MODE", "sometimes")::get));
    }

    @Test
    void rejectsAnInvalidLobbyPattern() throws IOException {
        assertThrows(IllegalArgumentException.class,
                () -> QueleaConfig.load(file(), Map.of("QUELEA_LOBBY_PATTERN", "([unclosed")::get));
    }

    @Test
    void readsTheCredentialsFromTheFile() throws IOException {
        writeFile("tenant-id=tenant-1\nclient-id=client-1\nclient-secret=file-secret\nauth-scope=api://api-1/.default\n");

        QueleaConfig config = QueleaConfig.load(file(), name -> null);

        assertTrue(config.credentials().isComplete());
        assertEquals("tenant-1", config.credentials().tenantId());
        assertEquals("client-1", config.credentials().clientId());
        assertEquals("file-secret", config.credentials().clientSecret());
        assertEquals("api://api-1/.default", config.credentials().scope());
    }

    @Test
    void credentialEnvironmentVariablesOverrideTheFile() throws IOException {
        writeFile("tenant-id=from-file\nclient-secret=from-file\n");

        QueleaConfig config = QueleaConfig.load(file(), Map.of(
                "QUELEA_TENANT_ID", "from-env",
                "QUELEA_CLIENT_ID", "client-env",
                "QUELEA_CLIENT_SECRET", "secret-env",
                "QUELEA_AUTH_SCOPE", "api://env/.default")::get);

        assertEquals("from-env", config.credentials().tenantId());
        assertEquals("client-env", config.credentials().clientId());
        assertEquals("secret-env", config.credentials().clientSecret());
        assertEquals("api://env/.default", config.credentials().scope());
    }

    @Test
    void missingCredentialsAreReportedByTheirConfigKeys() throws IOException {
        QueleaConfig config = QueleaConfig.load(file(), name -> null);

        assertFalse(config.credentials().isComplete());
        assertEquals(List.of("tenant-id", "client-id", "client-secret", "auth-scope"), config.credentials().missing());
    }

    @Test
    void writesTheCredentialKeysAsCommentedDefaults() throws IOException {
        QueleaConfig.load(file(), name -> null);

        String written = Files.readString(file(), StandardCharsets.UTF_8);
        assertTrue(written.contains("#client-secret=") && written.contains("QUELEA_CLIENT_SECRET"),
                "the secret key is documented with its environment variable");
        assertTrue(written.contains("#auth-scope="), "the scope key is documented");
    }
}
