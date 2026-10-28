# Design

## Context

Builds on `add-party-service` (archive that change first, its specs are the base of the deltas here). Parties live in
Dragonfly; `PartyService` serialises changes per party with `LockService`; `ProxyReaper` already shows the pattern for
cluster-wide periodic work (one replica per interval through `lock:proxy-reaper`). The SSE fan-out is one Reactor
`Sinks.many().multicast().directBestEffort()` per replica.

## Goals / Non-Goals

Goals: lonely parties disappear on time with an explanation; a slow stream never shows stale state silently; the stream
is usable by clients that are not proxies. Non-goals: exactly-once delivery, event replay, a new event envelope.

## Decisions

### 1. Lonely-party bookkeeping in two sorted sets

`parties:alone:warn` and `parties:alone:expire` hold party ids scored by the epoch milliseconds of the warning and the
expiry. Creating a party (explicit or by the first invite) adds it to both; the accept that brings the second member
removes it from both; deleting a party removes it from both. Because `removeMember` disbands a party that drops to one
member, a party only ever is alone right after its creation, so no other path has to touch the sets.

A `PartyExpiryJob` runs every `QUELEA_PARTY_EXPIRY_INTERVAL` (default `15s`) on every replica under the cluster lock
`party-expiry` (same pattern as `ProxyReaper`):
- due warnings: publish `party.expiring` with `expiresAt`, remove the entry;
- due expiries: under the party lock, re-read the party; if it still has one member, disband it with reason
  `expired-alone`; remove the entry either way.

The expiry is therefore at most one interval late (30 min + 15 s). `QUELEA_PARTY_ALONE_WARNING=0` disables the warning.
Time comes from the injected `Clock`, so tests drive it without waiting.

Rejected: a per-party key with TTL plus keyspace notifications (notifications are fire-and-forget, lost while no
replica listens, and only partly supported by Dragonfly); a timestamp scan over all parties (O(parties) per interval,
the sorted set makes it O(due)).

### 2. Disband reason

`disbandParty` already receives a reason string; it is now part of the `party.disbanded` payload as `reason`. Values:
`leader` (leader disbanded), `last-member-left` (only one member remained), `expired-alone`. Clients must treat unknown
values like `leader` without a special message.

### 3. Bounded delivery per stream

Each subscriber of the broadcaster gets `onBackpressureBuffer(QUELEA_SSE_BUFFER, overflow → error)`. The buffer
requests unbounded from the sink, so the sink never drops for that subscriber; when the HTTP connection cannot drain and
the buffer is full, the stream ends with an overflow signal, the controller logs it once at `WARN` (no stack trace) and
increments `quelea.sse.disconnects{reason="overflow"}`. The keep-alive `Flux.interval` gets `onBackpressureDrop()`, so a
missing tick never ends a stream. Closing is the right answer for a slow client: the plugin reconnects and sends its
snapshot, after which its state is correct again.

Rejected: a larger or unbounded buffer (memory grows with the slowest client); dropping events per subscriber (the
current behaviour: the client never learns it is stale).

### 4. Event envelope stays as it is

CloudEvents 1.0 was considered as a standard envelope. A conforming structured event would move `partyId`, `leaderId`,
`members` and `payload` into `data` (extension attribute names must be lower-case alphanumerics), which breaks the 0.1.0
plugin, while no consumer exists that reads CloudEvents. The current event already carries `id`, `type`, `occurredAt`
and `traceparent`. Rejected for now; revisit when a non-Java consumer appears.

### 5. Client label

`GET /v1/events` accepts `clientId` (optional, max 64 chars); `proxyId` is kept as an alias for the 0.1.0 plugin. The
label only appears in logs. With `secure-party-api`, the authenticated client id is logged as well.

### 6. Plugin messages

`party.expiring` → the leader (if local) gets `party.expiring` ("Your party closes in 5 minutes because nobody joined.
Invite someone with /party invite."; minutes computed from `expiresAt`). `party.disbanded` with `reason=expired-alone` →
the former leader gets `party.disbanded.expired-alone`; other reasons keep `party.disbanded`.

### 7. Configuration

`QUELEA_PARTY_ALONE_TIMEOUT` (`30m`), `QUELEA_PARTY_ALONE_WARNING` (`5m`), `QUELEA_PARTY_EXPIRY_INTERVAL` (`15s`),
`QUELEA_SSE_BUFFER` (`256`).

### 8. Project principle 2

`openspec/config.yaml` principle 2 changes from "one Server-Sent-Events stream per proxy" to "one stream per client
(proxy, lobby server, tool); a stream that falls behind its bounded buffer is closed and the client resynchronises".

## Risks / Trade-offs

- A party whose leader is still waiting for a friend after 30 minutes is closed; the leader can create a new one with
  one invite. The timeout is configurable.
- Closing a slow stream causes a reconnect plus snapshot; a client that is permanently too slow reconnects repeatedly,
  which the `quelea.sse.disconnects` counter makes visible.
