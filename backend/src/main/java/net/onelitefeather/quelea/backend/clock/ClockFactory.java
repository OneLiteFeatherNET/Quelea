package net.onelitefeather.quelea.backend.clock;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

import java.time.Clock;

@Factory
public class ClockFactory {

    @Singleton
    public Clock clock() {
        return Clock.systemUTC();
    }
}
