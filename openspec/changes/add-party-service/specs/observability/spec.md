# Spec Delta

## Purpose
Traces, metrics, logs and health.

## ADDED Requirements

### Requirement: Tracing
Where an OTLP exporter is configured, the service SHALL export a span for every HTTP request and for every event fan-out, and the fan-out span SHALL be linked to the span of the request that caused the event.

#### Scenario: Warp trace
- **WHEN** a leader's move causes a warp
- **THEN** the warp request and the fan-out of `party.warp` appear in one trace view

### Requirement: Metrics
The service SHALL expose Prometheus metrics at `/prometheus`, including party operations by operation and result, active parties, open event streams and published events by type.

#### Scenario: Operation counted
- **WHEN** an invite fails with `party-full`
- **THEN** `quelea_party_operations_total{operation="invite",result="party-full"}` increases

### Requirement: Logs and health
The service SHALL write JSON logs containing `trace_id` and `span_id` when `LOG_JSON` is true, and SHALL report unhealthy at `/health` while Dragonfly is unreachable.

#### Scenario: Dragonfly down
- **WHEN** Dragonfly is unreachable
- **THEN** `/health` answers 503
