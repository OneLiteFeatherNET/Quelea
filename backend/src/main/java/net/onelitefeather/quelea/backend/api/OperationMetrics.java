package net.onelitefeather.quelea.backend.api;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Singleton;
import net.onelitefeather.quelea.backend.problem.PartyException;
import net.onelitefeather.quelea.backend.problem.PartyProblem;

import java.util.function.Supplier;

/**
 * Counts every service call of the HTTP API as {@code quelea.party.operations{operation,result}}, where result is
 * {@code ok} or the problem code of the failure. Controllers route each call through here, so the metric has one
 * definition.
 */
@Singleton
public class OperationMetrics {

    static final String METRIC = "quelea.party.operations";

    private final MeterRegistry registry;

    public OperationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public <T> T record(String operation, Supplier<T> action) {
        String result = "ok";
        try {
            return action.get();
        } catch (PartyException e) {
            result = e.problem().code();
            throw e;
        } catch (RuntimeException e) {
            result = PartyProblem.INTERNAL_ERROR.code();
            throw e;
        } finally {
            registry.counter(METRIC, "operation", operation, "result", result).increment();
        }
    }

    public void run(String operation, Runnable action) {
        record(operation, () -> {
            action.run();
            return null;
        });
    }
}
