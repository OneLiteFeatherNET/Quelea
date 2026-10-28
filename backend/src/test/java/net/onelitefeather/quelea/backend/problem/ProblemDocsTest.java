package net.onelitefeather.quelea.backend.problem;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProblemDocsTest {

    private static final String DOCS_PATH = "docs/problems.md";

    @ParameterizedTest
    @EnumSource(PartyProblem.class)
    void documentsEveryCodeInASectionOfItsOwn(PartyProblem problem) throws IOException {
        String docs = readDocs();

        assertTrue(docs.lines().anyMatch(line -> line.equals("## " + problem.code())),
                "docs/problems.md must have a section '## " + problem.code() + "' for " + problem.name());
    }

    /** Gradle runs tests in the backend module directory; the docs live one level up. Walk up to be robust. */
    private static String readDocs() throws IOException {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve(DOCS_PATH);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
            dir = dir.getParent();
        }
        throw new IOException(DOCS_PATH + " not found above " + Path.of("").toAbsolutePath());
    }
}
