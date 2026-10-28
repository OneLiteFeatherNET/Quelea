package net.onelitefeather.quelea.velocity.config;

import net.onelitefeather.quelea.velocity.follow.FollowMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
