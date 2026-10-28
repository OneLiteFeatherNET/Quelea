package net.onelitefeather.quelea.backend.api;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricsResourceAttributesTest {

    @Test
    void exportedMetricsNameTheServiceAndThePod() {
        try (ApplicationContext context = ApplicationContext.builder(Environment.TEST)
                .properties(Map.of("HOSTNAME", "quelea-micronaut-7d9f-abcde", "OTEL_SERVICE_NAME", "quelea"))
                .build()) {
            context.getEnvironment().start();

            String attributes = context.getEnvironment()
                    .getProperty("micronaut.metrics.export.otlp.resource-attributes", String.class)
                    .orElseThrow();

            assertEquals("service.name=quelea,service.instance.id=quelea-micronaut-7d9f-abcde", attributes,
                    "each replica must report its pod name as service.instance.id, or the replicas' series collide");
        }
    }
}
