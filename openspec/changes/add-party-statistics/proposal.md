# Proposal

**Ships as:** `feat(observability)`: one code pull request `feat(observability): record party statistics`.

## Why

The team wants a Grafana dashboard that answers simple questions about parties: how many are open right now, how many
were opened and closed in the last 24 hours, why they closed, how long they live and how big they get, and how that
compares to the number of players online. Quelea's metrics today only answer the first question
(`quelea.parties.active`). Closed parties are only visible as published `party.disbanded` events without their reason,
and nothing describes lifetime, size or the players online.

## What Changes

- `quelea.parties.created` counter: one per new party.
- `quelea.parties.disbanded` counter with tag `reason` (`leader`, `last-member-left`, `expired-alone`).
- `quelea.party.lifetime` timer (histogram) with tag `reason`, recorded when a party is disbanded: time from creation.
- `quelea.party.size` distribution summary (histogram), recorded on every join: the member count after the join.
- `quelea.players.online` gauge: players currently online on any proxy, read from the presence store.
- A Grafana dashboard "Quelea" (Kubernetes-FLUX, not this repository) built on these meters plus the existing ones.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `observability`: party statistics.

## Out of Scope

- Per-player statistics or anything that identifies players in metrics (no player ids or names as tags).
- Follow success (whether members actually reached the leader's server): that happens on the proxy; a later change can
  add plugin metrics.

## Impact

- Backend: `PartyService` (or a small recorder it calls), presence store port, one gauge class, tests.
- Kubernetes-FLUX: dashboard JSON under `services/observability/clusters/feather-core/grafana/dashboards/applications/`.
