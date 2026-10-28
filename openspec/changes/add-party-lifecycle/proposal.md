# Proposal

**Ships as:** `feat(party)`: one code pull request `feat(party): close lonely parties and protect event streams`.

## Why

A party is created the moment a player sends the first invite. When nobody accepts, the player sits in a party of one
until they leave the network, and that empty party blocks nothing but still shows up in every `/party list` and every
active-party metric. Players expect such a party to go away on its own and to be told why.

A test against 0.1.0 showed a second gap on the event stream. A client that stops reading (a stuck proxy, a paused
debugger, a saturated link) does not slow down other clients, but it silently loses events and is only cut off by
accident after 32 missed keep-alive ticks, about eight minutes in production. During that time its view of every party
is stale without any signal. Every client of the stream (proxies today, the lobby and tools later) needs a stream that
either delivers everything or ends, so it can resynchronise.

## What Changes

- **Lonely parties close themselves.** A party that has had no second member for `QUELEA_PARTY_ALONE_TIMEOUT` (default
  30 minutes) since it was created is disbanded with the reason `expired-alone`. `QUELEA_PARTY_ALONE_WARNING` (default 5
  minutes) before that, the service publishes `party.expiring`, so the client can tell the leader. A party with two
  members never becomes lonely again, because the last partner leaving already disbands it.
- **`party.disbanded` names its reason** in the payload (`reason`), so a client can explain why the party is gone.
- **A stream that falls behind is closed.** Each open stream gets a bounded buffer (`QUELEA_SSE_BUFFER`, default 256
  events). When it overflows, the service ends that stream and counts it; the client reconnects and resynchronises from
  a snapshot, which the plugin already does. Keep-alive comments that cannot be written are dropped instead of failing
  the stream.
- **Any client can hold a stream.** `GET /v1/events` takes an optional `clientId` label; `proxyId` stays as an alias so
  the 0.1.0 plugin keeps working. Both are only used for logs.
- **Velocity plugin** tells the leader about `party.expiring` and explains `expired-alone` when the party is disbanded,
  in English and German.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `party-management`: lonely-party expiry.
- `party-events`: disband reason, expiry warning, bounded delivery per stream, client label.
- `velocity-plugin`: expiry messages.

## Out of Scope

- Replaying missed events after a reconnect (`Last-Event-ID`, Redis Streams). The snapshot after a reconnect already
  restores state; only transient events such as a chat line can be missed.
- CloudEvents as the event envelope (see design decision 4).
- Titan integration; it consumes the same stream later as its own client.

## Impact

- Backend: party service, a scheduled expiry job, Redis adapter, event controller, configuration.
- Plugin: event handler and message bundles.
- `openspec/config.yaml`: principle 2 now speaks of one stream per client and names the overflow rule.
