package net.onelitefeather.quelea.backend.api;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.onelitefeather.quelea.backend.problem.PartyException;
import net.onelitefeather.quelea.backend.problem.PartyProblem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OperationMetricsTest {

    @Test
    void successIsCountedAsOk() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new OperationMetrics(registry).record("invite", () -> "done");

        assertEquals(1.0, registry.get("quelea.party.operations")
                .tags("operation", "invite", "result", "ok").counter().count());
    }

    @Test
    void domainFailureIsCountedByItsProblemCode() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationMetrics metrics = new OperationMetrics(registry);

        assertThrows(PartyException.class,
                () -> metrics.record("invite", () -> {
                    throw new PartyException(PartyProblem.PARTY_FULL, "full");
                }));

        assertEquals(1.0, registry.get("quelea.party.operations")
                .tags("operation", "invite", "result", "party-full").counter().count(),
                "a failed invite must count under its problem code and still propagate");
    }

    @Test
    void unexpectedFailureIsCountedAsInternalError() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationMetrics metrics = new OperationMetrics(registry);

        assertThrows(IllegalStateException.class,
                () -> metrics.run("chat", () -> {
                    throw new IllegalStateException("boom");
                }));

        assertEquals(1.0, registry.get("quelea.party.operations")
                .tags("operation", "chat", "result", "internal-error").counter().count());
    }
}
