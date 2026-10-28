# Tasks

## 1. Lonely-party expiry (backend)

- [ ] 1.1 `PartySettings`: `aloneTimeout` (`QUELEA_PARTY_ALONE_TIMEOUT`, 30m), `aloneWarning`
      (`QUELEA_PARTY_ALONE_WARNING`, 5m), `expiryInterval` (`QUELEA_PARTY_EXPIRY_INTERVAL`, 15s).
- [ ] 1.2 Port methods for the two sorted sets (`markAlone`, `clearAlone`, `dueWarnings`, `dueExpiries`) with an
      in-memory fake and the Redis adapter (`parties:alone:warn`, `parties:alone:expire`).
- [ ] 1.3 `PartyService`: mark on create, clear on the accept that adds the second member and on delete; `expire(partyId)`
      that re-checks under the party lock and disbands with `expired-alone`; `warnExpiring(partyId)` publishing
      `party.expiring` with `expiresAt`.
- [ ] 1.4 `PartyExpiryJob` scheduled by `expiryInterval` under the `party-expiry` lock (as `ProxyReaper`).
- [ ] 1.5 `party.disbanded` payload carries `reason` (`leader`, `last-member-left`, `expired-alone`).
- [ ] 1.6 Unit tests with a fixed `Clock` (expiry, warning once, join cancels, two jobs disband once) and a Redis
      integration test for the sorted sets.

## 2. Event stream (backend)

- [ ] 2.1 Failing test first: a stalled subscriber must be closed after `QUELEA_SSE_BUFFER` events while another
      subscriber receives all; a missed keep-alive must not end the stream.
- [ ] 2.2 Per-subscriber bounded buffer with overflow → stream end, `quelea.sse.disconnects{reason}` counter, one
      `WARN` line without stack trace; keep-alive with `onBackpressureDrop()`.
- [ ] 2.3 `clientId` query parameter, `proxyId` as optional alias; OpenAPI updated.

## 3. Velocity plugin

- [ ] 3.1 `party.expiring` message for the local leader; `party.disbanded.expired-alone` message; en and de bundles and
      the `MessagesTest` key list.
- [ ] 3.2 Handler tests with fakes.

## 4. Documentation

- [ ] 4.1 README configuration table: the four new variables; event list: `party.expiring`, disband `reason`.

## 5. Pull request

- [ ] 5.1 Open the code pull request `feat(party): close lonely parties and protect event streams`.
