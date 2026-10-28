package net.onelitefeather.quelea.backend.party;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Singleton;

import java.time.Duration;

/**
 * Party meters recorded from the state changes of {@link PartyService}. Only reasons and sizes are tags or values;
 * no player id or name ever reaches a metric.
 */
@Singleton
public class PartyStatistics {

    static final String CREATED = "quelea.parties.created";
    static final String DISBANDED = "quelea.parties.disbanded";
    static final String LIFETIME = "quelea.party.lifetime";
    static final String SIZE = "quelea.party.size";

    private static final Duration[] LIFETIME_SLOS = {
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofMinutes(30),
            Duration.ofHours(1), Duration.ofHours(2), Duration.ofHours(4)
    };
    private static final double[] SIZE_SLOS = {2, 3, 4, 5, 6, 7, 8};

    private final MeterRegistry registry;

    public PartyStatistics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void created() {
        Counter.builder(CREATED)
                .description("Parties created")
                .register(registry)
                .increment();
    }

    /** Counts the disband by its reason and records how long the party lived. */
    public void disbanded(String reason, Duration lifetime) {
        Counter.builder(DISBANDED)
                .description("Parties disbanded, by reason")
                .tag("reason", reason)
                .register(registry)
                .increment();
        Timer.builder(LIFETIME)
                .description("Time from creation to disband of a party, by reason")
                .tag("reason", reason)
                .serviceLevelObjectives(LIFETIME_SLOS)
                .register(registry)
                .record(lifetime);
    }

    /** Records the member count of a party after a join. */
    public void joined(int size) {
        DistributionSummary.builder(SIZE)
                .description("Party size after each join, counting the leader")
                .serviceLevelObjectives(SIZE_SLOS)
                .register(registry)
                .record(size);
    }
}
