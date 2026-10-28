# Design

## Context

Metrics are Micrometer meters exported over OTLP (`export-metrics-via-otlp`) and land in Mimir with Prometheus names
(`quelea.parties.created` → `quelea_parties_created_total`). Every replica exports its own counters
(`service.instance.id` = pod); store-backed gauges such as `quelea.parties.active` report the same value on every
replica.

## Decisions

### 1. Record where the state changes

`PartyService` already knows every creation (`create`), join (`accept`) and disband (`disbandParty` with its reason) and
has the party's `createdAt` and the injected `Clock`. A small `PartyStatistics` class wraps the meters and is called
from these three places; the service stays free of Micrometer types beyond that collaborator, and tests use a
`SimpleMeterRegistry`.

Rejected: deriving everything from `quelea.events.published{type}` (no reason, no lifetime, no size); recording in the
controllers (the expiry job disbands without a request).

### 2. Histograms

`quelea.party.lifetime` is a `Timer` with SLO buckets 1 min, 5 min, 15 min, 30 min, 1 h, 2 h, 4 h; `quelea.party.size`
is a `DistributionSummary` with buckets 2…8 (the default maximum size) plus +Inf. Explicit buckets keep the series count
small and make `histogram_quantile` work in Mimir.

### 3. Players online

`quelea.players.online` sums `SCARD proxy:{id}:players` over the known proxies (one round trip per proxy, every export
step). Like `quelea.parties.active` it reports the same value on every replica; dashboards take `max`.

### 4. Dashboard (Kubernetes-FLUX)

Rows: **Now** (open parties, players online, players per party), **Last 24 h** (created, closed, closed by reason,
expired alone, invites sent and accepted, warps), **Trends** (open parties, created vs closed per hour, closures by
reason), **Behaviour** (lifetime p50/p90, party size distribution), **Health** (operation errors by code, HTTP rate and
latency, open streams, stream overflows, failed event publishes, lock timeouts). Counters are summed over replicas,
store gauges use `max`. Queries select `job=~"quelea.*"` so they cover the scrape job of 0.1.0 and the OTLP job of
0.2.0.

## Risks / Trade-offs

- Counters restart with each pod; `increase()` handles resets, so 24-hour totals stay correct across restarts.
- A party created before this release has no lifetime start in the meter's view; its lifetime is still computed from
  `createdAt`, so nothing is lost.
