package net.onelitefeather.quelea.backend.presence;

import net.onelitefeather.quelea.backend.party.port.LockService;
import net.onelitefeather.quelea.backend.problem.PartyException;
import net.onelitefeather.quelea.backend.problem.PartyProblem;
import net.onelitefeather.quelea.backend.support.PartyFixture;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyReaperTest {

    @Test
    void sweepRunsUnderTheProxyReaperLock() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        f.clock.advance(Duration.ofSeconds(46));
        String[] lockName = new String[1];
        LockService recording = new LockService() {
            @Override
            public <T> T withLock(String name, Supplier<T> action) {
                lockName[0] = name;
                return action.get();
            }
        };

        new ProxyReaper(recording, f.presenceService).sweep();

        assertEquals("proxy-reaper", lockName[0], "the sweep must hold the proxy-reaper lock");
        assertFalse(f.presence.find(alpha).isPresent(), "the sweep must have reaped the dead proxy");
    }

    @Test
    void sweepSkipsWhenAnotherReplicaHoldsTheLock() {
        PartyFixture f = new PartyFixture();
        UUID alpha = f.online("Alpha");
        f.clock.advance(Duration.ofSeconds(46));
        LockService busy = new LockService() {
            @Override
            public <T> T withLock(String name, Supplier<T> action) {
                throw new PartyException(PartyProblem.LOCK_TIMEOUT, "held elsewhere");
            }
        };

        new ProxyReaper(busy, f.presenceService).sweep();

        assertTrue(f.presence.find(alpha).isPresent(), "a skipped sweep must leave the state to the replica that holds the lock");
    }
}
