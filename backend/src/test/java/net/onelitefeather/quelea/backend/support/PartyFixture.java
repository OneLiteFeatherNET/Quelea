package net.onelitefeather.quelea.backend.support;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.onelitefeather.quelea.backend.party.PartyService;
import net.onelitefeather.quelea.backend.party.PartySettings;
import net.onelitefeather.quelea.backend.party.PartyStatistics;
import net.onelitefeather.quelea.backend.party.port.LockService;
import net.onelitefeather.quelea.backend.presence.PlayerPresence;
import net.onelitefeather.quelea.backend.presence.PresenceService;
import net.onelitefeather.quelea.backend.presence.PresenceSettings;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** A fresh, isolated service graph for one test: its own clock, stores, lock and recorder. */
public final class PartyFixture {

    public static final String PROXY = "proxy-1";

    public final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T12:00:00Z"));
    public final InMemoryPartyStore parties = new InMemoryPartyStore(clock);
    public final InMemoryPresenceStore presence = new InMemoryPresenceStore(clock);
    public final RecordingEventPublisher events = new RecordingEventPublisher();
    public final PartySettings partySettings = new PartySettings();
    public final PresenceSettings presenceSettings = new PresenceSettings();
    public final PartyService partyService;
    public final PresenceService presenceService;
    public final LockService locks = new InMemoryLockService(Duration.ofSeconds(1));
    public final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    public PartyFixture() {
        partySettings.setMaxSize(3);
        partySettings.setInviteTtl(Duration.ofSeconds(60));
        partyService = new PartyService(parties, presence, locks, events, partySettings, clock, new PartyStatistics(meters));
        presenceService = new PresenceService(presence, parties, partyService, presenceSettings, clock);
    }

    /** Puts a player online on {@link #PROXY}. */
    public UUID online(String name) {
        UUID id = UUID.randomUUID();
        presence.put(new PlayerPresence(id, name, PROXY, clock.instant()));
        presence.touchProxy(PROXY, presenceSettings.getProxyTimeout());
        return id;
    }
}
