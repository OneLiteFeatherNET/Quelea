package net.onelitefeather.quelea.velocity.stream;

import java.time.Duration;

/**
 * The wait between reconnects. Production uses {@link #SYSTEM}; tests inject a recording fake.
 */
@FunctionalInterface
public interface Sleeper {

    Sleeper SYSTEM = Thread::sleep;

    void sleep(Duration duration) throws InterruptedException;
}
