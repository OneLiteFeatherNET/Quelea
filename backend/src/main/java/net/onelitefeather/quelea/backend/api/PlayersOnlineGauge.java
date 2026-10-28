package net.onelitefeather.quelea.backend.api;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.context.annotation.Context;
import net.onelitefeather.quelea.backend.party.port.PresenceStore;

/** {@code quelea.players.online}, read from the presence store on every scrape. */
@Context
public class PlayersOnlineGauge {

    public PlayersOnlineGauge(MeterRegistry registry, PresenceStore presence) {
        Gauge.builder("quelea.players.online", presence, store -> {
                    try {
                        return store.countOnlinePlayers();
                    } catch (RuntimeException e) {
                        return Double.NaN;
                    }
                })
                .description("Players online on any proxy")
                .register(registry);
    }
}
