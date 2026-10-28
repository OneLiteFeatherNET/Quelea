package net.onelitefeather.quelea.backend.api;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.context.annotation.Context;
import net.onelitefeather.quelea.backend.party.port.PartyStore;

/** {@code quelea.parties.active}, read from the store on every scrape. */
@Context
public class ActivePartiesGauge {

    public ActivePartiesGauge(MeterRegistry registry, PartyStore parties) {
        Gauge.builder("quelea.parties.active", parties, store -> {
                    try {
                        return store.countParties();
                    } catch (RuntimeException e) {
                        return Double.NaN;
                    }
                })
                .description("Parties that currently exist")
                .register(registry);
    }
}
