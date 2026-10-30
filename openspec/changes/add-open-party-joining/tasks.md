# Tasks

Tests follow F.I.R.S.T.: unit tests use the in-memory fakes and an injected `Clock`; Redis tests are tagged `integration`.
No sleeps. Each test asserts one behaviour.

## 1. Configuration and model (backend)

- [ ] 1.1 `PartySettings`: `publicMaxSize` (`QUELEA_PARTY_PUBLIC_MAX_SIZE`, 20, minimum equal to `maxSize`),
      `joinRequestTtl` (`QUELEA_JOIN_REQUEST_TTL`, 60s), `joinRequestCooldown` (`QUELEA_JOIN_REQUEST_COOLDOWN`, 60s);
      defaults in `application.yml`.
- [ ] 1.2 `Party` gets `public` (boolean, default false). `withLeader` clears it. Tests: a promotion clears the flag, a
      member change keeps it, both bump the version.
- [ ] 1.3 `JoinRequest` record (partyId, requesterId, requesterName, expiresAt), `@Serdeable`.

## 2. Store port and Redis adapter (backend)

- [ ] 2.1 `PartyStore` methods: `saveJoinRequest(JoinRequest, Duration)`, `findJoinRequest`, `deleteJoinRequest`,
      `setCooldown(partyId, playerId, Duration)`, `cooldownLeft(partyId, playerId)`, `ban`, `unban`, `isBanned`,
      `deleteBans(partyId)`.
- [ ] 2.2 `InMemoryPartyStore` in tests implements them with an injected `Clock` for expiry.
- [ ] 2.3 `RedisKeys`: `join-request:<partyId>:<playerId>`, `join-cooldown:<partyId>:<playerId>`, `party:<partyId>:bans`.
- [ ] 2.4 `RedisPartyStore` adapter, with an `integration` test: request key expires at its TTL; the cooldown key is
      overwritten by a rejection; the ban set is deleted with the party.

## 3. Service rules (backend)

- [ ] 3.1 Failing tests first, in `PartyServiceTest` with a fixed `Clock`:
      request accepted, request denied, request expired, duplicate request, requester already in a party, target without
      a party, offline target, not-leader answer. Cooldown: denial at 10:00:00 gives 429 at 10:00:59 and 201 at
      10:01:00; expiry at 10:01:00 of a request created at 10:00:00 gives 429 at 10:01:59 and 201 at 10:02:00; an
      accepted request, later left, allows an immediate new request; a pending request answers `join-request-pending`,
      not 429.
- [ ] 3.2 `PartyService.requestJoin(requesterId, targetName)`: resolves the target by presence, creates the request
      under the party lock, or joins directly when the party is public (answers the party, not a request).
- [ ] 3.3 `PartyService.join(playerId, targetName)`: public parties only (`party-not-public` otherwise), ban check,
      `ensureRoom` with the public limit, publishes `party.joined`.
- [ ] 3.4 `PartyService.acceptRequest` and `rejectRequest` (leader only, `requesterName` resolved by presence): accept
      joins through the same path as an invite and deletes the request and the cooldown keys; reject deletes the request
      key and overwrites the cooldown with `px` = cooldown.
- [ ] 3.5 `PartyService.setPublic(partyId, actorId, public, precondition)`: leader only, bumps the version.
- [ ] 3.6 Kick: failing test first (kicked player blocked from join and request, ban ends with the party), then the ban
      write in the same locked command as the member removal.
- [ ] 3.7 Invite lifts the ban: failing test first (kicked player invited, then joins through the invite), then
      `unban` in the locked invite.
- [ ] 3.8 `ensureRoom` uses `publicMaxSize` for a public party and `maxSize` otherwise; a public party above the
      private limit rejects new members (test: closed with 12 members, invite answered `party-full`).
- [ ] 3.9 Leadership change (promote and succession) clears `public`; test for both. Keep-parties interaction (D10),
      failing tests first: a non-leader leave or kick that leaves one member keeps `public` and starts the alone period
      at the departure; a leader leave that leaves one member clears `public` and starts the alone period; a join ends
      the alone period.
- [ ] 3.10 Events: `JOIN_REQUESTED` (`party.join-requested`) and `JOIN_REQUEST_ANSWERED` (`party.join-request-answered`)
      added to `PartyEventType` with payloads as in the spec.

## 4. Problems (backend)

- [ ] 4.1 `PartyProblem`: `PARTY_NOT_PUBLIC`, `JOIN_REQUEST_PENDING`, `JOIN_REQUEST_COOLDOWN` (429), `JOIN_REQUEST_NOT_FOUND`,
      `PARTY_BANNED`, `TARGET_NOT_IN_PARTY`.
- [ ] 4.2 `Retry-After` header on `join-request-cooldown`; test with the seconds left.
- [ ] 4.3 `docs/problems.md`: one section per code (enforced by `ProblemDocsTest`).

## 5. API (backend)

- [ ] 5.1 `PartyController`: `POST /v1/parties/{partyId}/public` (`If-Match`), `POST /v1/parties/{partyId}/join-requests/accept`
      (`If-Match`), `POST /v1/parties/{partyId}/join-requests/reject`. Each with its OpenAPI annotations and the same
      `Idempotency-Key` behaviour as the other commands.
- [ ] 5.2 `PlayerController`: `POST /v1/players/{playerId}/party/join` (200 party) and
      `POST /v1/players/{playerId}/party/requests` (201 `JoinRequest` or 200 party for a public party).
- [ ] 5.3 `RouteCoverageTest` and `ConditionalRequestsTest` extended for the new routes; `oasdiff` run locally against
      `origin/main` (additive changes only).

## 6. Velocity plugin

- [ ] 6.1 `LocalPlayer.hasPermission(String)`; `VelocityLocalPlayers` implements it through Velocity; `FakeLocalPlayer`
      in tests answers from a set. Tests: `on` without the permission makes no call to the service; `off` without the
      permission calls the service.
- [ ] 6.2 `QueleaGateway` and `HttpQueleaGateway`: `setPublic`, `join`, `request` (a sealed result: request or party by
      status code), `acceptRequest`, `rejectRequest`. Tests with a local HTTP server for the 201/200 split and the 429
      `Retry-After`.
- [ ] 6.3 `PartyActions`: `publicOn`, `publicOff`, `join`, `request`, `approve`, `reject`. `FakeQueleaGateway` extended.
- [ ] 6.4 `PartyCommands`: `public on|off`, `request <player>`, `join <player>`, `approve <player>`, `reject <player>`;
      `HelpText` lines for them.
- [ ] 6.5 `EventHandler`: `party.join-requested` prompts the local leader with `[Approve]` and `[Reject]`;
      `party.join-request-answered` tells the local requester. Test each, plus an unknown type.
- [ ] 6.6 `messages_en.properties` and `messages_de.properties`: every new key, including `problem.party-not-public`,
      `problem.join-request-pending`, `problem.join-request-cooldown`, `problem.join-request-not-found`,
      `problem.party-banned` and `problem.target-not-in-party` in both (`FailureReports` treats a code without an English
      key as undocumented). `MessagesTest` `PROBLEM_CODES` extended with the six new codes.

## 7. Documentation

- [ ] 7.1 README: player command table (five new rows), configuration table (three new variables), the event list and
      the join rules under "How it works".

## 8. Verification

- [ ] 8.1 `./gradlew :backend:test -PexcludeTags=integration` and `./gradlew :velocity:test` green, no sleeps, no
      wall-clock reads in the new tests.
- [ ] 8.2 `./gradlew build` green.

## 9. Pull request

- [ ] 9.1 Open the code pull request `feat(party): let players ask to join and open parties to everyone` from a branch
      cut from the remote main branch that already contains the merged proposal. It contains no file under `openspec/`.
