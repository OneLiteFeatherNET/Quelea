package net.onelitefeather.quelea.velocity.stream;

import java.time.Duration;

/**
 * Exponential delay between reconnects: the first delay is {@code initial}, every following one doubles up to
 * {@code max}. {@link #reset()} starts over after a successful connect. Not thread-safe.
 */
public final class Backoff {

    private final Duration initial;
    private final Duration max;
    private Duration next;

    public Backoff(Duration initial, Duration max) {
        if (initial.isNegative() || initial.isZero() || max.compareTo(initial) < 0) {
            throw new IllegalArgumentException("need 0 < initial <= max");
        }
        this.initial = initial;
        this.max = max;
        this.next = initial;
    }

    /**
     * @return the delay to wait before the next attempt; advances the sequence
     */
    public Duration nextDelay() {
        Duration current = next;
        Duration doubled = next.multipliedBy(2);
        next = doubled.compareTo(max) > 0 ? max : doubled;
        return current;
    }

    public void reset() {
        next = initial;
    }
}
