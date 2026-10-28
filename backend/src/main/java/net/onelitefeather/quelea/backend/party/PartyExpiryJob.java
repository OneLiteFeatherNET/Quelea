package net.onelitefeather.quelea.backend.party;

import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import net.onelitefeather.quelea.backend.party.port.LockService;
import net.onelitefeather.quelea.backend.party.port.PartyStore;
import net.onelitefeather.quelea.backend.problem.PartyException;
import net.onelitefeather.quelea.backend.problem.PartyProblem;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Warns and closes lonely parties on every replica; the lock lets exactly one replica run each interval, the same
 * way the proxy reaper does. A run publishes the due warnings first, then expires the due parties.
 */
@Singleton
public class PartyExpiryJob {

    static final String LOCK_NAME = "party-expiry";

    private final LockService lockService;
    private final PartyStore parties;
    private final PartyService partyService;
    private final Clock clock;

    public PartyExpiryJob(LockService lockService, PartyStore parties, PartyService partyService, Clock clock) {
        this.lockService = lockService;
        this.parties = parties;
        this.partyService = partyService;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = "${quelea.party.expiry-interval:15s}")
    public void sweep() {
        try {
            lockService.withLock(LOCK_NAME, () -> {
                expireDue();
                return null;
            });
        } catch (PartyException e) {
            // Another replica holds the expiry lock and is doing this interval's work.
            if (e.problem() != PartyProblem.LOCK_TIMEOUT) {
                throw e;
            }
        }
    }

    private void expireDue() {
        Instant now = clock.instant();
        for (UUID partyId : parties.dueWarnings(now)) {
            partyService.warnExpiring(partyId);
        }
        for (UUID partyId : parties.dueExpiries(now)) {
            partyService.expire(partyId);
        }
    }
}
