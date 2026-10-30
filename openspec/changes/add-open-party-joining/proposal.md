# Proposal

**Ships as:** `feat(party)`: one code pull request `feat(party): let players ask to join and open parties to everyone`.

## Why

Today a player can only enter a party through an invite from its leader. A player who wants to join a friend has to ask
in chat, and the friend then has to find the right command. Streamers cannot let their viewers in at all without
inviting each one by hand, which does not scale past a handful of people.

Two things are asked for. A player should be able to ask a party to let them in, and the leader should be able to say
the party is open, so that anyone who knows the leader's name can walk in. Both must keep the party under the control of
its leader: a join request is answered by the leader, and a kicked player does not come back on their own.

## What Changes

- **Join requests.** `/party request <player>` sends a request to the party of that player. The leader gets a clickable
  prompt and accepts or rejects it. A request expires after 60 seconds, like an invite. A player has at most one open
  request per party, and after an expiry or a rejection may ask again only after a cooldown (60 seconds by default).
- **Public parties.** A leader with the Velocity permission `quelea.party.public` opens the party with `/party public on`.
  Any leader may close it with `/party public off`, so a leader who lost the permission can still close. While it is
  open, `/party join <player>` puts the player straight in, with no request.
- **Own size for public parties.** A public party holds up to `QUELEA_PARTY_PUBLIC_MAX_SIZE` members (20 by default).
  Private parties keep `QUELEA_PARTY_MAX_SIZE` (8). A full party answers with the existing `party-full` problem.
- **Discovery by name only.** There is no list and no broadcast of public parties.
- **Kick ban.** A kicked player cannot join or request that party while it exists. An explicit invite from the leader
  lifts the ban for that player. The ban list belongs to the party and disappears with it.
- **Velocity plugin** gains the commands `public`, `request`, `join`, `approve` and `reject`, the help lines for them, and
  the messages in English and German.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `party-management`: join requests, public parties, kick ban, public size limit, leadership change closes a public party.
- `party-events`: two new events for the leader's prompt and the requester's answer.
- `velocity-plugin`: the new commands, the permission check, the messages.
- `problem-details`: six new documented codes.

The metrics need no new meter. The existing `quelea_party_operations_total{operation,result}` counts the new operations
by name, so `observability` does not change.

## Out of Scope

- Listing or announcing public parties (no `/party list public`, no broadcast in chat).
- Password-protected parties.
- Party bans that outlive the party. A ban ends with the party.
- Redirecting chat with `@name` to a join request.
- Notifying the members when the leader opens or closes the party. The party state carries the flag; no event is added.
- Telling the leader that a request expired. Expiry is a TTL in Dragonfly and publishes nothing.

## Impact

- Backend: `api/PartyController` (public, join, join-requests), `api/PlayerController` (join, requests), `party/Party`
  (public flag), `party/PartyService`, `party/PartySettings`, `party/port/PartyStore` and its Redis adapter, `event`
  (two types), `problem/PartyProblem` (six codes), OpenAPI (generated from annotations; no committed client exists).
- Velocity: `command/PartyCommands`, `command/PartyActions`, `command/HelpText`, `event/EventHandler`,
  `player/LocalPlayer` (permission check), `http/QueleaGateway` and `HttpQueleaGateway`, `lang/messages_en` and
  `messages_de`.
- Documentation: `docs/problems.md` (a section per new code, enforced by `ProblemDocsTest`), README command and
  configuration tables.
- Configuration: `QUELEA_PARTY_PUBLIC_MAX_SIZE`, `QUELEA_JOIN_REQUEST_TTL`, `QUELEA_JOIN_REQUEST_COOLDOWN`, with defaults
  in `application.yml`. No Kubernetes change is needed.
