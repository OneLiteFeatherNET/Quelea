package net.onelitefeather.quelea.backend.presence;

import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import net.onelitefeather.quelea.backend.party.port.LockService;
import net.onelitefeather.quelea.backend.problem.PartyException;
import net.onelitefeather.quelea.backend.problem.PartyProblem;

/** Runs the dead-proxy sweep on every replica; the lock lets exactly one replica do it per interval. */
@Singleton
public class ProxyReaper {

    static final String LOCK_NAME = "proxy-reaper";

    private final LockService lockService;
    private final PresenceService presenceService;

    public ProxyReaper(LockService lockService, PresenceService presenceService) {
        this.lockService = lockService;
        this.presenceService = presenceService;
    }

    @Scheduled(fixedDelay = "${quelea.presence.reaper-interval:30s}")
    public void sweep() {
        try {
            lockService.withLock(LOCK_NAME, () -> {
                presenceService.reap();
                return null;
            });
        } catch (PartyException e) {
            // Another replica holds the sweep lock and is doing this interval's work.
            if (e.problem() != PartyProblem.LOCK_TIMEOUT) {
                throw e;
            }
        }
    }
}
