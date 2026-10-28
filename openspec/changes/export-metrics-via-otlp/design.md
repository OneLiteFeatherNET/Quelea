# Design

## Context

The backend uses Micrometer (via `micronaut-micrometer`) for its own meters and for the HTTP and JVM meters Micronaut
provides. Traces already go through the OpenTelemetry SDK to `alloy-receiver.grafana.svc:4317`.

## Decisions

### 1. Micrometer OTLP registry instead of the OpenTelemetry metrics API

`micronaut-micrometer-registry-otlp` pushes every Micrometer meter over OTLP/HTTP (protobuf). Configuration:

```yaml
micronaut.metrics.export.otlp:
  enabled: ${QUELEA_METRICS_OTLP_ENABLED:false}
  url: ${QUELEA_METRICS_OTLP_URL:`http://localhost:4318/v1/metrics`}
  step: ${QUELEA_METRICS_OTLP_STEP:60s}
```

Resource attributes `service.name=quelea` and `service.instance.id` (pod name from `HOSTNAME`).

Rejected: the OpenTelemetry metrics API for Quelea's own meters (two metric systems side by side, because Micronaut's
HTTP and JVM meters come from Micrometer); the OpenTelemetry Micrometer bridge (more moving parts for the same wire
format); keeping Prometheus next to OTLP (the point is one path).

### 2. Removing `/prometheus`

The Prometheus registry dependency and the endpoint go away; the anonymous rule for `/prometheus` from
`secure-party-api` is removed with it. A test asserts the route answers 404 (`not-found` problem).

### 3. Project principle 4

`openspec/config.yaml` principle 4 becomes "OpenTelemetry tracing and metrics over OTLP (metrics through the Micrometer
OTLP registry), JSON logs with trace ids".

## Risks / Trade-offs

- Between deploying the new image and the FLUX change, metrics are not exported (OTLP disabled by default); the FLUX
  pull request sets the variables in the same rollout.
- Push means a dead replica stops reporting instead of failing a scrape; alerts must use absence of the series.
