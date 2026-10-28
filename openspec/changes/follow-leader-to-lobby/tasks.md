# Tasks

## 1. Backend

- [ ] 1.1 `WarpRequest.fromServer` (optional, max 64); `PartyService.warp` puts it into the `party.warp` payload as
      `from`; OpenAPI updated.
- [ ] 1.2 Tests: warp with and without origin.

## 2. Plugin

- [ ] 2.1 Server-connected listener: a leader's move with a previous server requests a warp with `fromServer`; no
      warp without a previous server.
- [ ] 2.2 `FollowPolicy`: follow if mode `always`, member on the origin, or member on a lobby; skip the leader and
      members already on the target.
- [ ] 2.3 Tests with fakes: game → lobby (co-player follows, member in another game stays, other lobby instance
      aligns), lobby → game (as before), lobby → lobby, first login, event without `from`.

## 3. Documentation

- [ ] 3.1 README: the follow rule.

## 4. Pull request

- [ ] 4.1 Open the code pull request `feat(party): bring the party back to the lobby with its leader`.
