# Proposal

**Ships as:** `fix(party)`: one code pull request `fix(party): keep a party alive after its other members leave`.

## Why

A player creates a party, invites a friend, and the two play for an hour. The friend's connection drops, or they
leave to get something to eat, and the party is gone the moment they leave: the player who stayed is told that the
party was disbanded because the last member left. When the friend reconnects a few minutes later, there is no party
to rejoin. The player has to create one again and invite everyone again.

The alone rule already exists for parties that never got a second member: they close after the alone timeout. The
team decided on 2026-10-28 that "alone" means the same thing for a party that has lost its other members: no second
member for the alone timeout, counted from creation or from the moment the last other member left. Only the creation
case was built. A party that drops to one member through a leave or a kick is still disbanded on the spot.

## What Changes

- **A party that drops to one member stays open.** When a leave or a kick leaves exactly one member, the party keeps
  its id and its remaining member. If the leader left, leadership passes by the existing succession rule (the
  longest-standing remaining member). The party is not disbanded.
- **The alone timeout restarts at that moment.** The party gets the same alone timeout as a new party
  (`QUELEA_PARTY_ALONE_TIMEOUT`, default 30 minutes), counted from the moment it became alone. The alone warning
  (`QUELEA_PARTY_ALONE_WARNING`, default 5 minutes) is sent before that end, as today.
- **A second member cancels the timer.** A join ends the alone period. If the party becomes alone again later, the timer
  starts again from that new moment.
- **A party that drops to zero members is disbanded at once** with `last-member-left`, as today.
- **The end of the timer is reported with its real time.** `party.expiring` carries the moment the alone timeout ends,
  counted from the start of the current alone period, not from the creation of the party.
- **The remaining member is told what happened.** The velocity plugin tells the remaining member, in English and
  German, that the others left and that the party closes if nobody joins for a while.

A party written by an older backend, without the new stored timestamp, still loads and expires by its creation time as
before.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `party-management`: lonely-party expiry counts from the start of the current alone period; a departure that leaves one
  member keeps the party open.
- `party-events`: the disband reason `last-member-left` is used only when no member remains; `party.expiring` carries
  the real expiry time.
- `velocity-plugin`: the remaining member is told that the party stays open after a leave or a kick.

## Out of Scope

- A different timeout for parties that lost members. Nobody asked for one; the existing setting applies.
- A grace period that only applies to reconnects.
- Keeping a party of zero members, or re-inviting on behalf of a player.
- Changing how public parties are joined. That is `add-open-party-joining`; the two changes are compatible in either
  order (see design).
- Archiving `add-party-lifecycle`. It is still unarchived; this change's deltas refer to its requirements.

## Impact

- Backend: `PartyService` (leave, kick, accept, expiry and warning), `Party` (one new nullable field), the Redis
  adapter for the alone sets (`markAlone` must also clear a stale warning), tests with a fixed `Clock`.
- Velocity plugin: one new message key in English and German for the remaining member, the event handler for
  `party.left` and `party.kicked`, tests.
- Statistics: `last-member-left` becomes rarer. `quelea.party.lifetime` keeps its meaning (creation to disband) and the
  dashboard keeps working; `expired-alone` lifetimes become longer for parties that lost members.
- API: no change to the HTTP surface. The `party.expiring` `expiresAt` value is now correct for parties that lost
  members.
