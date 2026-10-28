# Proposal

**Ships as:** `feat(party)` — one code pull request `feat(party): add distributed party service and velocity plugin`.
The Kubernetes rollout is a separate `feat(quelea)` pull request in `Kubernetes-FLUX`, not part of this repository.

## Why

The network has no way for friends to play together. Two players who want the same round of a minigame have to pick the
same game in the lobby at the same moment and hope that the lobby's "lowest players" selection puts them on the same
instance; often it does not. A party system is the usual answer: players group up, and when the party leader joins a
game, the party follows onto the same server.

The network runs several game servers behind Velocity, and later possibly several proxies, so the party must live in one
place that every proxy can see and must react within a second when the leader moves.

## What Changes

- **New Micronaut service `backend`** that owns parties: create, invite (by player name, network-wide), accept, deny,
  leave, kick, promote, disband, warp, party chat. Party state lives in Dragonfly (Redis protocol) and is ephemeral; any
  number of replicas can serve requests.
- **Network presence**: proxies report logins, disconnects and a periodic heartbeat, so the service can resolve player
  names to players on any proxy and clean up parties of players who left the network, including when a proxy dies.
- **Real-time events**: every change publishes an event; replicas fan it out over Redis pub/sub; each proxy holds one
  Server-Sent-Events stream (`GET /v1/events`) and receives every event within milliseconds.
- **Party follows the leader**: when the leader's proxy sees the leader connect to a non-lobby server, it asks the
  service to warp the party; every proxy moves its local members to **that exact server** (not to the task), so the party
  never splits across instances. By default only members who are in a lobby follow; members in another game stay.
- **New Velocity plugin `velocity`**: `/party` (alias `/p`) with `create`, `invite`, `accept`, `deny`, `leave`, `kick`,
  `promote`, `disband`, `list`, `warp`, and `/pc <message>` for party chat; messages in English and German; clickable
  accept/deny on invites. The lobby (Titan) needs no change: it already sends the leader to a game through CloudNet, and
  the proxy plugin reacts to that move.
- **Generated Java client `client`** from the service's OpenAPI document, used by the plugin and published for other
  consumers (e.g. a later Titan party item).
- **RFC 9457 Problem Details** for every error (`application/problem+json`) with a stable `code`, documented in
  `docs/problems.md`.
- **Observability**: OpenTelemetry traces (OTLP), trace context carried inside events, Prometheus metrics at
  `/prometheus`, JSON logs with `trace_id`/`span_id`, health at `/health` including Dragonfly.
- **Release**: Release Please builds the Docker image `harbor.onelitefeather.dev/onelitefeather/quelea`, publishes the
  client and plugin to the OneLiteFeather Maven repository and attaches the plugin jar to the GitHub release.

## Capabilities

### New Capabilities
- `party-management`: party lifecycle, membership rules, invites, limits.
- `network-presence`: which player is online on which proxy; disconnect and dead-proxy cleanup.
- `party-events`: event model, fan-out across replicas, the SSE stream for proxies.
- `party-follow`: warping a party to the leader's server.
- `problem-details`: the error contract.
- `observability`: tracing, metrics, logs, health.
- `velocity-plugin`: commands, messages, listeners and the event consumer on the proxy.

### Modified Capabilities
None (new project).

## Out of Scope

- Persistence across service restarts of Dragonfly: parties are lost if Dragonfly loses its data; players simply create
  a new party.
- Authentication inside the service. It is reachable only on the internal hostname; trust is the network boundary.
- Changes to Titan (a party item or party view in the lobby) and to minigames (keeping a party in one team, letting a
  party member in after a round started). Both can build on the published client later.
- Party size reservations on the target server: if a game is full, members that do not fit get a message and stay.
- Friends lists, party finder, persistent party settings.
- The Kubernetes manifests (separate `Kubernetes-FLUX` pull request) and installing the plugin into the CloudNet
  template (operator step on the CloudNet node).

## Impact

- New repository `OneLiteFeatherNET/Quelea` with modules `backend`, `client`, `velocity`.
- New keys under the prefix `quelea:` and the pub/sub channel `quelea.events` on the shared Dragonfly.
- Velocity proxies need the plugin jar and `QUELEA_URL`.
