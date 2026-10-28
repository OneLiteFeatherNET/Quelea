package net.onelitefeather.quelea.backend.api;

import io.micronaut.context.ApplicationContext;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Timers must leave the OTLP exporter in seconds so that Mimir gets the *_seconds_* series the dashboard reads. */
class OtlpExportConfigTest {

    private ApplicationContext context;

    @BeforeEach
    void startContext() {
        context = ApplicationContext.builder()
                .environments("test")
                .properties(Map.of(
                        "micronaut.metrics.export.otlp.enabled", "true",
                        "micronaut.metrics.export.otlp.url", "http://127.0.0.1:9/v1/metrics"))
                .start();
    }

    @AfterEach
    void stopContext() {
        context.close();
    }

    @Test
    void otlpRegistryExportsTimersInSeconds() throws Exception {
        OtlpMeterRegistry registry = context.getBean(OtlpMeterRegistry.class);

        assertEquals(TimeUnit.SECONDS, otlpConfig(registry).baseTimeUnit(),
                "the OTLP registry must export timers in seconds, not the Micrometer default of milliseconds");
    }

    /** The registry keeps its configuration in a private field; reading it is the only way to see the base unit. */
    private static OtlpConfig otlpConfig(OtlpMeterRegistry registry) throws Exception {
        Field field = OtlpMeterRegistry.class.getDeclaredField("config");
        field.setAccessible(true);
        return (OtlpConfig) field.get(registry);
    }
}
