package net.onelitefeather.quelea.velocity.stream;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BackoffTest {

    private static Backoff plugin() {
        return new Backoff(Duration.ofSeconds(1), Duration.ofSeconds(30));
    }

    @Test
    void doublesTheDelayUpToTheMaximum() {
        Backoff backoff = plugin();

        assertEquals(Duration.ofSeconds(1), backoff.nextDelay());
        assertEquals(Duration.ofSeconds(2), backoff.nextDelay());
        assertEquals(Duration.ofSeconds(4), backoff.nextDelay());
        assertEquals(Duration.ofSeconds(8), backoff.nextDelay());
        assertEquals(Duration.ofSeconds(16), backoff.nextDelay());
        assertEquals(Duration.ofSeconds(30), backoff.nextDelay(), "32 s is capped at 30 s");
        assertEquals(Duration.ofSeconds(30), backoff.nextDelay(), "the cap holds");
    }

    @Test
    void resetStartsOverAtTheInitialDelay() {
        Backoff backoff = plugin();
        backoff.nextDelay();
        backoff.nextDelay();
        backoff.nextDelay();

        backoff.reset();

        assertEquals(Duration.ofSeconds(1), backoff.nextDelay());
    }

    @Test
    void rejectsAMaximumBelowTheInitialDelay() {
        assertThrows(IllegalArgumentException.class,
                () -> new Backoff(Duration.ofSeconds(5), Duration.ofSeconds(1)));
    }

    @Test
    void rejectsANonPositiveInitialDelay() {
        assertThrows(IllegalArgumentException.class,
                () -> new Backoff(Duration.ZERO, Duration.ofSeconds(1)));
    }
}
