# Design

## Context

`FollowPolicy.shouldTriggerWarp` warps only when the leader connects to a non-lobby server; `FollowPolicy` then
moves local members who are in a lobby (mode `lobby`) or everyone (mode `always`). Velocity's `ServerConnectedEvent`
carries the previous server, so the proxy knows where the leader came from.

## Decisions

### 1. Rule: "were together, or idle in a lobby"

On `party.warp` with target `T` and origin `F`, a local member `M` on server `S` (not the leader, `S ≠ T`) follows if
mode is `always`, or `S = F`, or `S` is a lobby. The rule needs no notion of "game" vs "lobby" for the target, so
lobby → lobby alignment falls out of it, and a member in another game is never pulled out.

### 2. When the proxy asks for a warp

Every `ServerConnectedEvent` of a party leader that has a previous server requests a warp to the new server with
`fromServer` = previous server. The first connection after login (no previous server) requests none. Members moved by
a warp are not leaders, so warps never cascade. `/party warp` sends no origin, so it keeps today's behaviour (members
in a lobby follow).

### 3. API

`WarpRequest` gains `fromServer` (optional, at most 64 characters); the service copies it into the `party.warp`
payload as `from`. Old plugins send no origin and ignore the field in events; a new plugin talking to an old service
gets events without `from` and falls back to the lobby rule. No version negotiation is needed.

Rejected: tracking each member's "last server with the leader" in the service (more state, and the proxy already
sees the move); warping only when the leader returns to a lobby from a game (lobby → lobby alignment would still be
missing).

## Risks / Trade-offs

- A leader hopping between lobby instances drags lobby members along each time; that is the intent (the party stays
  together) and costs one server switch per member.
- A member who just left the round on their own to another lobby instance is moved to the leader's lobby; acceptable,
  since they are idle in a lobby.
