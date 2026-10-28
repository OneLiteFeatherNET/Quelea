# Design

## Context

Players reach the network through Velocity on the CloudNet node; game servers (Titan lobbies, minigames) are CloudNet
services that Velocity knows by service name (e.g. `Lobby-1`, `Bounce-2`). Titan sends a player to a minigame with the
CloudNet bridge (`connectToTask(task, LOWEST_PLAYERS)`), so two separate requests for the same task can land on two
instances. Shared infrastructure in the cluster: Dragonfly (Redis protocol, `dragonfly.dragonfly.svc:6379`), the Alloy
OTLP receiver (`alloy-receiver.grafana.svc:4317`), Prometheus via `ServiceMonitor`, internal hostnames
`*.apps.onelite.feather` through the Envoy gateway. There is no RabbitMQ; NATS exists but needs per-client credentials
issued by hand.

## Goals / Non-Goals

Goals: one consistent party state for any number of proxies and service replicas; the party lands on the leader's exact
server within about a second; clear errors; traces from command to warp.
Non-goals: see the proposal's "Out of Scope".

## Decisions

### 1. Modules and stack

Gradle multi-project like Blackhole: `backend` (Micronaut 5.0.2, Netty, Java 25, `micronaut-redis-lettuce`,
`micronaut-serde-jackson`, `micronaut-validation`, `micronaut-openapi`, `micronaut-management`, Micrometer Prometheus,
`micronaut-tracing-opentelemetry-http`, OTLP exporter, logstash encoder), `client` (OpenAPI Generator `java`/`native`,
Jackson), `velocity` (Velocity API 4.3.1 `compileOnly`, shadow jar, the client shaded in). The version catalog is inline
in `settings.gradle.kts` (as in Blackhole). The Docker image comes from Micronaut's `optimizedDockerfile`, running as a
non-root user.

The client is generated **from the backend's own OpenAPI document at build time** (`micronaut.openapi` writes
`META-INF/swagger/quelea.yml` with a fixed file name; `client:openApiGenerate` reads it from the backend's compiled
classes). This removes the manual spec copy step that Blackhole has.

### 2. State in Dragonfly, keys and atomicity

All keys share the prefix `quelea:` (configurable, `QUELEA_REDIS_PREFIX`).

| Key | Type | Content | TTL |
|---|---|---|---|
| `party:{partyId}` | string (JSON) | id, leaderId, members [{id, name, joinedAt}], createdAt | none |
| `player:{playerId}:party` | string | partyId | none |
| `invite:{partyId}:{playerId}` | string (JSON) | partyId, inviterId, inviterName, targetId, expiresAt | invite TTL (60 s) |
| `player:{playerId}:invites` | set | partyIds with an invite (stale entries filtered on read) | none |
| `presence:{playerId}` | string (JSON) | id, name, proxyId, since | none |
| `name:{lowercase name}` | string | playerId | none |
| `proxy:{proxyId}:players` | set | playerIds on that proxy | none |
| `proxy:{proxyId}:alive` | string | last heartbeat | heartbeat TTL (45 s) |
| `proxies` | set | known proxyIds | none |
| `lock:party:{partyId}` / `lock:proxy-reaper` | string | lock token | lock TTL |

Every mutation of a party runs under a per-party lock: `SET lock:party:{id} <token> NX PX 3000`, retried for up to
1 s, released by a compare-and-delete Lua script. A player can be in at most one party: joining claims
`player:{id}:party` with `SET NX` inside the lock and rolls back the party write if the claim fails. Reads do not lock.

Storage sits behind a `PartyStore` / `PresenceStore` / `LockService` port; unit tests use in-memory implementations,
the Redis implementations are covered by Testcontainers tests tagged `integration`.

Rejected: Postgres (state is ephemeral, and the shared CNPG cluster is near its connection limit); Lua scripts for every
operation (atomic, but business rules in Lua are hard to test and review); `WATCH/MULTI` (needs a dedicated connection
per transaction with Lettuce and retries under contention).

### 3. Party rules

- Size limit `QUELEA_PARTY_MAX_SIZE` (default 8), counting the leader.
- `invite` by the leader only; the target is looked up by name in the network presence; inviting when the inviter has
  no party creates one. An invite expires after `QUELEA_INVITE_TTL` (default 60 s). Re-inviting refreshes the TTL.
- `accept` needs a live invite and the player must not be in a party; `deny` drops the invite.
- `leave`: a member leaves; when the leader leaves, the longest-standing member becomes leader; an empty party (or a
  party of one after a leave) is disbanded.
- `kick`, `promote`, `disband`, `warp` are leader-only. `chat` is member-only, 1–256 characters.
- A player who disconnects from the network leaves their party (same rule as `leave`) and their invites are dropped.

### 4. HTTP API (v1)

All bodies are JSON; all errors are Problem Details (decision 6). `actorId` is the player who issued the command, as
reported by the proxy.

| Method and path | Body | Success |
|---|---|---|
| `POST /v1/parties` | `{leaderId}` | 201 Party |
| `GET /v1/parties/{partyId}` | – | 200 Party |
| `GET /v1/players/{playerId}/party` | – | 200 Party / 404 `not-in-party` |
| `POST /v1/parties/invites` | `{actorId, targetName}` | 201 Invite (creates the party if needed) |
| `GET /v1/players/{playerId}/invites` | – | 200 [Invite] |
| `POST /v1/players/{playerId}/invites/{partyId}/accept` | – | 200 Party |
| `POST /v1/players/{playerId}/invites/{partyId}/deny` | – | 204 |
| `POST /v1/players/{playerId}/party/leave` | – | 204 |
| `POST /v1/parties/{partyId}/kick` | `{actorId, targetId}` | 200 Party |
| `POST /v1/parties/{partyId}/promote` | `{actorId, targetId}` | 200 Party |
| `POST /v1/parties/{partyId}/disband` | `{actorId}` | 204 |
| `POST /v1/parties/{partyId}/warp` | `{actorId, server}` | 202 |
| `POST /v1/parties/{partyId}/chat` | `{actorId, message}` | 202 |
| `PUT /v1/proxies/{proxyId}/players` | `[{id, name}]` | 204 (full snapshot, replaces the set) |
| `PUT /v1/proxies/{proxyId}/players/{playerId}` | `{name}` | 204 (login) |
| `DELETE /v1/proxies/{proxyId}/players/{playerId}` | – | 204 (disconnect) |
| `PUT /v1/proxies/{proxyId}/heartbeat` | – | 204 |
| `GET /v1/events?proxyId=` | – | 200 `text/event-stream` |

Commands are POSTs on a resource instead of generic PATCHes, because each one has its own rule and its own event.

### 5. Events and real-time delivery

Every successful mutation publishes one event after the state write:
`{id, type, partyId, occurredAt, members:[{id,name}], leaderId, payload, traceparent}` with `type` one of
`party.created`, `party.invited`, `party.joined`, `party.denied`, `party.left`, `party.kicked`, `party.leader-changed`,
`party.disbanded`, `party.warp`, `party.chat`. `members` is the member list after the change (for `party.left`,
`party.kicked` and `party.disbanded` the payload also names the affected players), so a proxy can notify its local
players without another request.

Fan-out: the replica that handled the request publishes the event JSON to the Redis channel `quelea.events`; every
replica subscribes and writes it to all its open SSE streams. A proxy keeps one stream (`GET /v1/events`); the server
sends a comment line every 15 s as keep-alive. Delivery is at-most-once: an event lost during a reconnect is not
replayed. This is acceptable because proxies hold no party state — every command reads the current state from the
service — and the only time-critical event, `party.warp`, is caused by an action the leader can repeat
(`/party warp`).

The proxy reconnects with exponential backoff (1 s doubling to 30 s), and after every (re)connect it sends a presence
snapshot.

The gateway route for the service disables the request timeout (`timeouts.request: 0s`), otherwise Envoy would cut the
stream; this is part of the Kubernetes pull request.

Rejected: NATS (credentials have to be minted by hand per client, and the plugin would need a second protocol and
library — SSE keeps the proxy on plain HTTP); WebSocket (the stream is one-way); CloudNet channel messages (the service
cannot see them and it ties the design to CloudNet).

### 6. Problem Details (RFC 9457)

One record `Problem(type, title, status, detail, instance, code, traceId, errors)` serialised as
`application/problem+json`. `type` is `https://github.com/OneLiteFeatherNET/Quelea/blob/main/docs/problems.md#<code>`,
so every type URI resolves to its documentation. `code` is the stable machine-readable slug the plugin switches on.
The domain throws `PartyException(PartyProblem, detail)`; `PartyProblem` is an enum carrying code, status and title.
One global exception handler maps `PartyException`, bean-validation failures (400 `validation-failed`, with `errors`),
unknown routes (404 `not-found`), wrong methods (405), and everything else (500 `internal-error`, no message leaked).

Codes: `validation-failed`, `not-found`, `party-not-found`, `not-in-party`, `already-in-party`, `not-leader`,
`not-member`, `party-full`, `player-not-online`, `invite-not-found`, `cannot-target-self`, `lock-timeout` (503),
`internal-error`.

Rejected: `micronaut-problem-json` (pulls in the Zalando problem library and its own mapping; the in-house record is a
few lines and matches the plan Blackhole settled on).

### 7. Party follow

The plugin listens to `ServerConnectedEvent`. If the player is the leader of a party (one `GET
/v1/players/{id}/party`) and the new server does **not** match the lobby pattern (`QUELEA_LOBBY_PATTERN`, default
`(?i)^lobby.*`), it calls `warp` with the server's name. Every proxy receiving `party.warp` connects each local member
(except the leader) whose current server is a lobby (`QUELEA_FOLLOW_MODE=lobby`, default) or any other server
(`always`) and differs from the target, using `player.createConnectionRequest(server)`. A failed connection (server full,
round started) tells the member why and leaves them where they are. `/party warp` triggers the same event manually.

### 8. Presence and dead proxies

Each proxy has an id (`QUELEA_PROXY_ID`, default: the CloudNet service name from the `CLOUDNET_SERVICE_NAME`-style
environment if present, otherwise a random UUID per start). Login and disconnect update presence; a heartbeat every
15 s refreshes `proxy:{id}:alive` (TTL 45 s). A reaper on every replica runs every 30 s under the
`lock:proxy-reaper` lock; for each proxy in `proxies` without `alive` key, it treats all its players as disconnected
and removes the proxy. A full snapshot on (re)connect removes players who are no longer on that proxy.

### 9. Observability

- Traces: `micronaut-tracing-opentelemetry-http`, exporter configured by `OTEL_TRACES_EXPORTER` (default `none`),
  `OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_SERVICE_NAME=quelea`. The publishing replica writes the W3C `traceparent` into
  the event, and the SSE fan-out starts a span linked to it, so a warp is traceable from the leader's request.
- Metrics: `quelea.party.operations{operation,result}` counter, `quelea.parties.active` gauge (maintained counter key),
  `quelea.sse.streams` gauge, `quelea.events.published{type}` counter, plus JVM/HTTP defaults at `/prometheus`.
- Logs: logback with `LOG_JSON` switch (logstash encoder), OpenTelemetry MDC for `trace_id`/`span_id`.
- Health: `/health` with the Redis indicator; readiness fails while Dragonfly is unreachable.

### 10. Configuration (backend)

`REDIS_URI` (`redis://localhost:6379`), `QUELEA_REDIS_PREFIX` (`quelea:`), `QUELEA_PARTY_MAX_SIZE` (8),
`QUELEA_INVITE_TTL` (`60s`), `QUELEA_PROXY_TIMEOUT` (`45s`), `QUELEA_SSE_KEEPALIVE` (`15s`), `LOG_JSON` (false),
`OTEL_*`. Plugin: `QUELEA_URL` (`http://localhost:8080`), `QUELEA_PROXY_ID`, `QUELEA_LOBBY_PATTERN`,
`QUELEA_FOLLOW_MODE`; each may also be set in `plugins/quelea/config.properties`, environment wins.

## Risks / Trade-offs

- **Shared Dragonfly**: a Dragonfly outage stops parties (503 from the service, plugin shows "party service
  unavailable"). The memory footprint is a few KiB per party.
- **Lost events during a reconnect**: a warp can be missed; the leader can repeat it with `/party warp`.
- **Lock TTL**: a replica that stalls longer than 3 s inside a lock can lose it; operations are short and the window is
  accepted.
- **TLS to the internal hostname**: the proxy must trust the internal CA for `https://quelea.apps.onelite.feather`, as
  it does for Otis.
