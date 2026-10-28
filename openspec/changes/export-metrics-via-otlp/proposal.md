# Proposal

**Ships as:** `feat(observability)`: one code pull request `feat(observability): push metrics over otlp`.

## Why

Quelea sends traces over OTLP but exposes metrics for a Prometheus scrape at `/prometheus`. Two paths mean two pieces of
configuration in the cluster (an OTLP endpoint and a ServiceMonitor), and the metrics endpoint sits on the same public
route as the API. The network's direction is OpenTelemetry end to end; the cluster's Alloy receiver already accepts OTLP
metrics and writes them to Mimir (Sturnus and Reposilite use it).

## What Changes

- **Metrics are pushed over OTLP/HTTP** with the Micrometer OTLP registry to `QUELEA_METRICS_OTLP_URL` every
  `QUELEA_METRICS_OTLP_STEP` (default 60 s), enabled by `QUELEA_METRICS_OTLP_ENABLED` (default false, so local runs and
  tests export nothing). The resource carries `service.name=quelea` and the replica's instance id.
- **`/prometheus` and the Prometheus registry are removed.**
- Metric names and tags stay; the existing Micrometer meters (party operations, active parties, open streams, published
  and failed events, stream disconnects, JVM and HTTP) keep their meaning.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `observability`: metrics export.

## Out of Scope

- Logs over OTLP: JSON logs on stdout are already collected by the cluster.
- Code changes in the Velocity plugin: with the OpenTelemetry Java agent on the proxy, outgoing `java.net.http` calls are
  traced and carry `traceparent` without plugin code.
- The Kubernetes-FLUX change (environment variables, removing the ServiceMonitor), merged by the maintainer.

## Impact

- Backend: dependencies, `application.yml`, `ActivePartiesGauge` and other meters unchanged.
- `openspec/config.yaml`: principle 4 now says metrics are pushed over OTLP.
- Grafana: metric names in Mimir follow the OTLP-to-Prometheus translation (`quelea_party_operations_total` stays);
  dashboards need no change beyond the job label.
