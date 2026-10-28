package net.onelitefeather.quelea.backend.support;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import net.onelitefeather.quelea.backend.party.port.LockService;
import net.onelitefeather.quelea.backend.party.port.PartyStore;
import net.onelitefeather.quelea.backend.party.port.PresenceStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Stands in for the Dragonfly adapters in the test environment, where Redis is disabled. The stores are in-memory
 * and the clock only moves when a test advances it. Each test class gets its own context, so no state is shared.
 */
@Factory
@Requires(env = "test")
public class TestPortsFactory {

    @Singleton
    MutableClock testClock() {
        return new MutableClock(Instant.parse("2026-01-01T12:00:00Z"));
    }

    @Singleton
    @Replaces(Clock.class)
    Clock clock(MutableClock clock) {
        return clock;
    }

    @Singleton
    PartyStore partyStore(Clock clock) {
        return new InMemoryPartyStore(clock);
    }

    @Singleton
    PresenceStore presenceStore(Clock clock) {
        return new InMemoryPresenceStore(clock);
    }

    @Singleton
    LockService lockService() {
        return new InMemoryLockService(Duration.ofSeconds(1));
    }
}
