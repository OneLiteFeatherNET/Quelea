# Design

## Context

`PartyService.removeMember` (used by leave and kick) disbands any party that is left with at most one member, with reason
`last-member-left`. The lonely-party rules from `add-party-lifecycle` mark a party alone at creation, clear the mark on
join, and compute the warning and expiry from `createdAt`. Warnings and expiries live in two Redis sorted sets
(`parties:alone:warn`, `parties:alone:expire`), written by `markAlone`, read by `PartyExpiryJob`.

## Decisions

### 1. Reuse the alone timeout

A party that drops to one member gets the same `QUELEA_PARTY_ALONE_TIMEOUT` and `QUELEA_PARTY_ALONE_WARNING` as a new
party. Rejected: a separate `left-alone-timeout` setting. Nobody asked for a different duration, and one setting keeps
the operator's table in the README short.

### 2. Stored timestamp: `aloneSince` on the party

`Party` gets one nullable field, `aloneSince` (Instant). It is set to `createdAt` on creation, set to the departure
time when a leave or a kick leaves exactly one member, and cleared to `null` when a join makes the party two-member.
The alone end is `(aloneSince ?? createdAt) + aloneTimeout`. The warning time is that end minus the warning.

A party stored without the field (written before this change) reads `null` and therefore expires by `createdAt` exactly
as before. No migration is needed; Dragonfly keys are ephemeral.

Rejected alternatives:
- Read the expiry from the score of `parties:alone:expire`. The sorted sets are derived indexes; a missing or cleared
  entry would then change what `party.expiring` says. The state belongs to the party record, the index only orders the
  work.
- Rewrite `createdAt` when the party drops to one member. That would change `quelea.party.lifetime` and every event's
  meaning of `createdAt`, to serve one field.
- Keep a separate `aloneSince` key per party in Redis. One more key to create, expire and delete on every disband, for a
  value that already belongs to the party record.

### 3. `markAlone` clears a stale warning

`markAlone(partyId, expireAt, null)` currently only adds the expiry entry. When a party restarts its alone period with the
warning disabled, an old warning entry could still fire at the old time. `markAlone` therefore removes the warning entry
when there is no warning time. The restart in the second join-and-leave cycle is covered by a Redis integration test.

### 4. Leave and kick share one path

`kick` already calls `removeMember`. A kick that leaves one member therefore follows the same rule as a leave: the party
stays, the remaining member becomes leader if the leader was removed, and the alone period starts. No second code path.

### 5. Events for the departure

A leave or kick that leaves one member publishes the departure event (`party.left` or `party.kicked`) with the remaining
members, then `party.leader-changed` if leadership moved, as a party with more members does. It publishes no
`party.disbanded`. The wire format is unchanged.

### 6. Mixed versions with velocity plugin 0.4.0

Checked against the 0.4.0 tag (`EventHandler`, `messages_en.properties`):
- `party.left` and `party.kicked` are handled generically. The remaining member is in `event.members()`, so 0.4.0 sends
  them the existing "X left the party." or "X was kicked from the party." line. No exception, no missing member.
- `party.leader-changed` for a remaining leader is handled (`party.leader-changed.self`).
- `party.expiring` is handled (`expiresAt` parsed, minutes rounded up, sent to the leader if local). It arrives 25 minutes
  after the departure, not at the departure.
- `party.disbanded` with `expired-alone` is handled and sent to the former members.

So 0.4.0 copes with a one-member party after a leave. The gap is the notice at the departure: the message says that
someone left and nothing says the party stays open. The new key covers that, see decision 7. A 0.4.0 plugin without the
key keeps the generic line and still gets the warning and the expiry messages.

### 7. Message for the remaining member

Existing keys (en and de): `party.left`, `party.kicked` and `party.expiring`. `party.expiring` says "closes in N minutes
because nobody joined", which is not the situation after a leave, and no key says the party stays open for a while.
A new key `party.left.alone` (and `party.kicked.alone`, if the wording differs for kicks) is sent to the single remaining
member after `party.left` or `party.kicked` leaves one member. Its text names the departure and says the party closes if
nobody joins for a while, without a minute count, because the event carries no expiry time.

### 8. Overlap with `add-open-party-joining`

That change keeps the lonely expiry unchanged for public parties. This change adds the alone period for parties that lost
members, for any party type. The two do not touch the same branch: the expiry rule is the same for both, and a public party
simply keeps its current join path. Whichever lands first, the other rebases onto it; no shared field is renamed.

## Rejected alternatives (summary)

- A separate `left-alone-timeout`: no requirement for a different duration.
- A short grace period only for reconnects: a reconnect can take longer than any grace period, and a party that is never
  closed is the case this change removes; the same timeout covers the reconnect case.
- Keeping `last-member-left` for every drop to one member: contradicts the requirement that a party stays open.
