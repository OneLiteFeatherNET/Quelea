package net.onelitefeather.quelea.backend.party;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityTagsTest {

    @Test
    void strongTagOfTheVersionIsTheVersionInQuotes() {
        assertEquals("\"4\"", EntityTags.of(4), "the strong tag of version 4 must be \"4\"");
    }

    @Test
    void strongComparisonMatchesTheCurrentVersion() {
        assertTrue(EntityTags.matchesStrong("\"4\"", 4), "the current strong tag must match");
    }

    @Test
    void strongComparisonRejectsAnotherVersion() {
        assertFalse(EntityTags.matchesStrong("\"3\"", 4), "an older tag must not match");
    }

    @Test
    void strongComparisonNeverMatchesAWeakTag() {
        assertFalse(EntityTags.matchesStrong("W/\"4\"", 4), "a weak tag must not satisfy If-Match");
    }

    @Test
    void anyListedTagMayMatch() {
        assertTrue(EntityTags.matchesStrong("\"3\", \"4\"", 4), "one matching tag in the list is enough");
    }

    @Test
    void wildcardMatchesAnyVersion() {
        assertTrue(EntityTags.matchesStrong("*", 9), "* matches any existing party");
    }

    @Test
    void weakComparisonIgnoresTheWeakPrefix() {
        assertTrue(EntityTags.matchesWeak("W/\"4\"", 4), "If-None-Match compares weakly");
    }
}
