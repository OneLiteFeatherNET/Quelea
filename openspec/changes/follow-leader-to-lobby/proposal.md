# Proposal

**Ships as:** `feat(party)`: one code pull request `feat(party): bring the party back to the lobby with its leader`.

## Why

The first test on the team proxy showed the follow working one way only. When the leader went from `Lobby-2` to
`Bounce-1`, the member followed within a second. When the leader returned with `/lobby`, the member stayed in the
finished round and, after typing `/lobby` themselves, landed on `Lobby-1` while the leader was on `Lobby-2`. The
party falls apart exactly when players expect it to stay together: after a round, and in the lobby itself, where the
lobby's own server selection knows nothing about parties.

## What Changes

- **One follow rule for every move of the leader:** a member follows the leader to the new server if the member was
  on the server the leader just left (they were together), or if the member is in a lobby. A member in another game
  stays. This covers game → lobby (the round ends), lobby → game (as today) and lobby → other lobby instance.
- **The leader's lobby moves trigger a warp too** (today only moves to non-lobby servers do).
- **Warps name their origin:** `POST /v1/parties/{id}/warp` takes an optional `fromServer`; the `party.warp` event
  carries it as `from`. The 0.2.0 plugin ignores it and keeps its old behaviour.
- Follow mode `always` is unchanged: every member follows every move.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `party-follow`: when a warp is requested and who follows.

## Out of Scope

- Party-aware lobby selection in Titan (sending the whole party to one lobby instance at login). The proxy aligns the
  party after the leader's first move instead.
- Moving members out of a game they are playing in follow mode `lobby`.

## Impact

- Backend: warp request and event payload (`fromServer` / `from`), OpenAPI.
- Plugin: server-connected listener, `FollowPolicy`, event handler, tests.
