# Design

## Context

Builds on `add-party-service` (its specs are the base of the deltas here; that change is not archived yet, so a delta
against its requirements may warn). Parties and invites live in Dragonfly. `PartyService` mutates a party under the lock
`party:<partyId>`. An invite targets a player by name through `PresenceStore.findByName`, which reads the network-wide
name index, so a target on another proxy resolves the same way as one on the leader's proxy. The plugin answers every
command through `PartyActions` and reacts to events in `EventHandler`.

## Goals / Non-Goals

Goals: a player can ask a party to let them in; a leader can open the party to everyone; the leader keeps control
(a kicked player does not come back on their own; a leadership change never leaves an open party to a new leader who
did not choose it).
Non-goals: discovery of public parties, passwords, bans that outlive a party, a request history.

## Decisions

### D1. The permission is checked on the proxy only

`quelea.party.public` is a Velocity permission, needed to open a party only. The plugin checks it before it calls
`POST /v1/parties/{partyId}/public` with `public: true`. Closing (`public: false`) needs no permission: closing is the
safe direction, and a leader who lost the permission must still be able to close the party.
The backend has no idea of Minecraft permissions (principle 2 and the Minecraft-independence principle); it trusts the
authenticated client, as it already trusts the client for every other command.
Rejected: a permission model in the backend (it would need the proxy's permission data in Dragonfly or a call back to
the proxy, both against principle 2).

### D2. Leadership change closes a public party

Promote and automatic succession clear `public`, bump the version, and publish the event as usual. The backend cannot
know whether the new leader holds `quelea.party.public`, so it does not guess. The new leader reopens the party if
allowed. Members stay, even above the private limit; new joins are then answered `party-full`. The same applies when a
leader closes a party above the private limit: the members stay, new joins are rejected as full.
Rejected: a stored "may open" flag per player (a second permission model in the backend); kicking the surplus members
(a leader closing the party must not remove people).

A leader who lost the permission while the party is open can still close it: closing needs no permission (D1).

D2 also applies when a public party drops to one member through a leave or a kick (see D10). A non-leader leaving
leaves the party public and starts its alone period. A leader leaving makes the successor leader, and that closes the
party as above.

### D3. An explicit invite lifts the kick ban

The ban only blocks self-service entry (`join`, `requests`). An invite from the leader removes the target from the ban
set in the same locked command that creates the invite. The leader's explicit act outranks the ban.
Rejected: a ban that also blocks invites (the leader could never undo a kick by inviting again).

### D4. Request on a public party joins directly; join on a private party is refused

`POST /v1/players/{playerId}/party/requests` on a public party performs the join and answers `200 Party`. On a private
party it creates a request and answers `201 JoinRequest`. The plugin tells the two apart by the status code. The player
therefore never sees an error for asking a public party. `POST /v1/players/{playerId}/party/join` on a private party
answers `409 party-not-public`, whose message points to `/party request`.
Rejected: one endpoint that always creates a request (a public party would then need a second answer step for nothing).

### D5. Target and requester checks

The target is resolved by name through presence, as an invite does (`player-not-online` when offline). A target that is
not in a party answers `404 target-not-in-party`. The reused code `not-in-party` would say "You are not in a party" to
the requester, which is wrong, so a new code is used. A requester already in a party answers `already-in-party`. A
requester who is not online answers `player-not-online`.

### D6. Lonely-party expiry applies to public parties unchanged

A public party with only one member expires after `QUELEA_PARTY_ALONE_TIMEOUT` like any other, counted from its alone
period (`aloneSince`, see D10; the creation time when it was never alone since). Rejected: exempting open parties, which
would leave forgotten open parties around for ever.

### D7. State: Dragonfly only, TTL keys, the version covers the party

- Public flag: the field `public` on the `Party` record. Every change bumps the version, so ETags and `If-Match` cover it
  as they cover membership.
- Join requests: key `<prefix>join-request:<partyId>:<playerId>`, JSON, `px` = `QUELEA_JOIN_REQUEST_TTL`. The key is
  the duplicate check and the expiry.
- Cooldown: key `<prefix>join-cooldown:<partyId>:<playerId>`. Its three writes give these exact rules (each is a scenario
  in the `party-management` delta, "Request cooldown"):
  - Creating a request sets `px` to TTL + cooldown. While the request is pending it is the duplicate check; after an
    expiry, which publishes nothing, it blocks the player until exactly the cooldown has passed since the expiry.
  - A rejection overwrites the key with `px` = cooldown, so a fresh cooldown of exactly that length starts at the
    moment of the denial.
  - An accepted request deletes the key, so a player who joined, later left, and asks again is not held back.
- Ban set: a Redis set `<prefix>party:<partyId>:bans` with the player ids, deleted in `disbandParty`. Deviation from
  "on the party": the ban set is not in the party JSON, so the proxies do not receive the ids of kicked players. The
  kick that adds a ban already bumps the version in the same locked command, so `If-Match` and ETags cover the change.
- Writes follow the existing `Idempotency-Key` and `If-Match` behaviour. The API change is additive (new routes, a new
  field, new problem codes), so oasdiff stays green.

Rejected: a join-request list in the party JSON (the leader would then see requests of people who are not in the party
state, and the field would grow with every request); a relational table (principle 1).

### D8. New events for the prompt and the answer

Names follow the existing `party.<verb>` convention:
- `party.join-requested` (payload `requesterId`, `requesterName`, `expiresAt`): the plugin tells the leader, if local.
- `party.join-request-answered` (payload `requesterId`, `requesterName`, `outcome` = `accepted` or `denied`): the plugin
  tells the requester, if local.

Follow, warp and chat are unchanged. A warp with 20 members can hit a full server; the existing connect-failure handling
applies to every member, unchanged.
Accepted requests also publish `party.joined` to the members, as an invite does. Expiry publishes nothing (see Risks).

### D9. Metrics: no new meter

The existing `quelea_party_operations_total{operation,result}` counts the new operations by name (`public`, `join`,
`request`, `accept`, `reject`). That is enough to see requests and answers without a new meter, and no player id is ever
a tag. `observability` does not change.

### D10. Interaction with keep-parties-after-members-leave

`keep-parties-after-members-leave` (merged as a proposal, fix in progress) keeps a party that a leave or a kick reduces
to one member. The party keeps its id, and its alone period starts at the departure, stored as `aloneSince`; the alone
end is `(aloneSince ?? createdAt) + QUELEA_PARTY_ALONE_TIMEOUT`. This change builds on it as follows:

- **Non-leader departure keeps a public party open.** When a member other than the leader leaves or is kicked and one
  member remains, the party stays public. It keeps its id, its ban set and its flag, and starts the alone period at the
  departure.
- **Leader departure closes it.** When the leader leaves or is kicked, the successor becomes leader, and D2 clears
  `public`. The alone period still starts at the departure.
- **Expiry is unchanged (D6).** It reads the same `aloneSince ?? createdAt` value; this change adds no field.
- **A join ends the alone period** as keep-parties specifies, for public and private parties alike.
- **Overlap.** The keep-parties design says the two changes do not touch the same branch. They do share
  `PartyService.removeMember`, where the leadership change clears `public`. Whichever change lands second rebases onto
  the first; the test for the leader departure (task 3.9) covers that path.

Rejected: a separate alone period for public parties, which would make a party's expiry depend on its visibility.

### Command and endpoint names

| Concept | Name |
|---|---|
| Open (permission) or close (any leader) | `POST /v1/parties/{partyId}/public`, body `{actorId, public}`, `If-Match` |
| Direct join | `POST /v1/players/{playerId}/party/join`, body `{targetName}` |
| Request | `POST /v1/players/{playerId}/party/requests`, body `{targetName}`: 201 request or 200 party |
| Accept request | `POST /v1/parties/{partyId}/join-requests/accept`, body `{actorId, requesterName}`, `If-Match` |
| Reject request | `POST /v1/parties/{partyId}/join-requests/reject`, body `{actorId, requesterName}` |
| Problems | `party-not-public` (409), `join-request-pending` (409), `join-request-cooldown` (429, `Retry-After`), `join-request-not-found` (404), `party-banned` (403), `target-not-in-party` (404) |
| Events | `party.join-requested`, `party.join-request-answered` |
| Commands | `/party public on|off`, `/party request <player>`, `/party join <player>`, `/party approve <player>`, `/party reject <player>` |

The leader's clickable prompt runs `/party approve <name>` and `/party reject <name>`. The name is resolved by the
backend, as invites are, so the proxy keeps no request state. `/party accept` and `/party deny` stay the invitee's
commands.

Rejected for the answer: a request id in the path (the plugin would have to cache the id from the event, and a proxy
restart would lose it).

## Mixed-version compatibility

Checked in the code on `origin/main`:

- **Unknown event types are ignored.** `EventHandler.handle` has a `default` branch that does nothing, with a comment
  saying so on purpose. An old plugin receiving `party.join-requested` or `party.join-request-answered` ignores them.
- **Unknown JSON fields are ignored.** `QueleaJson` and `PartyEvent.parse` read named keys from Gson `JsonObject`s and
  never reject extra keys. A new `public` field on the party JSON and new payload keys are ignored by a 0.4.x plugin.
- **New plugin, old backend.** The new routes answer `404 not-found`; the plugin shows the existing `problem.not-found`
  text. Nothing else changes for the old backend.
- **Old plugin, new backend.** Old plugins never call the new routes, so they cannot create requests or public parties.
- **Real gap: a leader on an old proxy.** A request made through a new proxy reaches the leader's proxy only through the
  event. If that proxy runs an old plugin, the prompt is silently dropped and the request expires. The requester sees
  "request sent" and nothing else. Mitigation is part of the rollout: install the new plugin on every proxy before the
  first player uses `/party request`. Nothing in the backend can detect the old plugin.

## Risks / Trade-offs

- A requester whose request expires gets no message, and the leader's prompt expires silently. The request answer
  says how long the request lasts, so the requester is not left guessing. Publishing an expiry event would need a
  scheduled job like the lonely-party expiry; that is a later change if players ask for it.
- A public party above 8 members that the leader then closes keeps its members, with no way to add more until the size
  drops. Accepted: no member is removed by a setting change.
- A player who was kicked can still be targeted by a leader's invite at any time; the invite lifts the ban, by design (D3).
- A party disbanded with a request still open leaves the request and cooldown keys to expire on their own, within the
  request TTL and cooldown. Their party id is never reused, so they cannot block a later party.
