# Spec Delta

## Purpose
Traces, metrics, logs and health.

## MODIFIED Requirements

### Requirement: Metrics
Where OTLP metrics export is enabled, the service SHALL push its metrics over OTLP/HTTP to the configured endpoint at the configured step, including party operations by operation and result, active parties, open event streams, stream disconnects by reason and published events by type, and SHALL NOT expose a Prometheus scrape endpoint.

#### Scenario: Operation counted
- **WHEN** an invite fails with `party-full`
- **THEN** the next export contains an increased `quelea.party.operations` with `operation="invite"` and `result="party-full"`

#### Scenario: No scrape endpoint
- **WHEN** a client calls `/prometheus`
- **THEN** the service answers 404 `not-found`
