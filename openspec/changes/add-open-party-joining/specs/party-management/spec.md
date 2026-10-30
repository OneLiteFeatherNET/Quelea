# Spec Delta

## Purpose
Party membership rules: join requests, public parties, kick ban and the size limits.

## ADDED Requirements

### Requirement: Join requests
A player who is not in a party SHALL be able to ask the party of an online player to let them in. The service SHALL resolve the target name case-insensitively against the network presence and keep at most one open request per party and requester, for `QUELEA_JOIN_REQUEST_TTL` (default 60 seconds). The leader SHALL accept or reject it.

#### Scenario: Request accepted
- **GIVEN** party P is private with leader A, and player B is not in a party
- **WHEN** B requests a join to "a" and A accepts B
- **THEN** B is a member of P and the request is gone

#### Scenario: Request denied
- **GIVEN** B holds an open request to P
- **WHEN** A rejects B
- **THEN** the request is gone and B is in no party

#### Scenario: Request expires
- **GIVEN** B requested P 60 seconds ago and A did not answer
- **WHEN** A accepts B now
- **THEN** the service answers 404 `join-request-not-found` and the party is unchanged

#### Scenario: Duplicate request
- **GIVEN** B holds an open request to P
- **WHEN** B requests P again
- **THEN** the service answers 409 `join-request-pending` and holds one request only

#### Scenario: Requester already in a party
- **WHEN** a player who is in a party requests another party
- **THEN** the service answers 409 `already-in-party`

#### Scenario: Target without a party
- **WHEN** a player requests the party of a target who is online but not in a party
- **THEN** the service answers 404 `target-not-in-party`

#### Scenario: Offline target
- **WHEN** a player requests a name that is not online on any proxy
- **THEN** the service answers 404 `player-not-online`

#### Scenario: Answer by someone else
- **WHEN** a member who is not the leader accepts or rejects a request
- **THEN** the service answers 403 `not-leader`

### Requirement: Request cooldown
After a request ends by rejection or by expiry, the service SHALL refuse a new request from the same player to the same party for `QUELEA_JOIN_REQUEST_COOLDOWN` (default 60 seconds), answering 429 `join-request-cooldown` with `Retry-After`. An accepted request SHALL clear the cooldown. The cooldown is measured from the denial after a rejection, and from the expiry, which is the end of the request's TTL, after an expiry.

#### Scenario: Cooldown after a denial
- **GIVEN** A rejects B's request to P at 10:00:00
- **WHEN** B requests P at 10:00:59
- **THEN** the service answers 429 `join-request-cooldown` with `Retry-After: 1`
- **AND** a request at 10:01:00 is held as a new request (201)

#### Scenario: Cooldown after an expiry
- **GIVEN** B's request to P was created at 10:00:00 and expired unanswered at 10:01:00
- **WHEN** B requests P at 10:01:59
- **THEN** the service answers 429 `join-request-cooldown` with `Retry-After: 1`
- **AND** a request at 10:02:00 is held as a new request (201)

#### Scenario: Accepted request clears the cooldown
- **GIVEN** B's request to P was accepted and B later left P
- **WHEN** B requests P at once
- **THEN** the request is held (201) and no cooldown applies

#### Scenario: Pending request is not a cooldown
- **GIVEN** B holds an open request to P
- **WHEN** B requests P again
- **THEN** the answer is 409 `join-request-pending`, not 429

### Requirement: Public parties
The service SHALL open or close a party on `POST /v1/parties/{partyId}/public`, from the party leader only. While a party is public, any online player not in a party SHALL be able to join it directly, answered 200 with the party. Every change of the flag SHALL bump the party version. The service needs no permission to close; the proxy checks it for opening only.

#### Scenario: Public join
- **GIVEN** party P is public with leader A
- **WHEN** B joins P by name "a"
- **THEN** B is a member of P and a `party.joined` event is published

#### Scenario: Request on a public party joins directly
- **GIVEN** party P is public
- **WHEN** B requests P by name
- **THEN** the service answers 200 with the party, B is a member, and no request is held

#### Scenario: Join on a private party refused
- **GIVEN** party P is private
- **WHEN** B joins P by name
- **THEN** the service answers 409 `party-not-public` and B is in no party

#### Scenario: Public party full at 20
- **GIVEN** public party P with 20 members
- **WHEN** B joins P
- **THEN** the service answers 409 `party-full` and P is unchanged

#### Scenario: Only the leader changes the flag
- **WHEN** a member who is not the leader sets the public flag
- **THEN** the service answers 403 `not-leader`

#### Scenario: Closing after the permission was lost
- **GIVEN** leader A has a public party and no longer holds `quelea.party.public`
- **WHEN** A closes the party
- **THEN** the service accepts the change and the party is not public

### Requirement: Kick ban
A player kicked from a party SHALL NOT join or request that party while it exists. An invite from the leader to that player SHALL lift the ban for that player. The ban SHALL end with the party.

#### Scenario: Kicked player blocked from join
- **GIVEN** B was kicked from public party P
- **WHEN** B joins P
- **THEN** the service answers 403 `party-banned`

#### Scenario: Kicked player blocked from request
- **GIVEN** B was kicked from private party P
- **WHEN** B requests P
- **THEN** the service answers 403 `party-banned` and no request is held

#### Scenario: Invite lifting the ban
- **GIVEN** B was kicked from P
- **WHEN** the leader of P invites B
- **THEN** B holds the invite, and B can join P through it

#### Scenario: Ban ends with the party
- **GIVEN** B was kicked from P and P is disbanded
- **WHEN** the same leader creates a new party and B asks to join it
- **THEN** B is not banned from the new party

## MODIFIED Requirements

### Requirement: Size limit
If a party already has the maximum number of members for its visibility, then the service SHALL reject invites, accepts, join requests and joins for it with 409 `party-full`. The maximum is `QUELEA_PARTY_PUBLIC_MAX_SIZE` for a public party and `QUELEA_PARTY_MAX_SIZE` for a private one. A party that is above the private maximum when it is closed keeps its members and rejects new members.

#### Scenario: Full party
- **GIVEN** a private party with the maximum number of members
- **WHEN** an invited player accepts
- **THEN** the service answers 409 `party-full` and the party is unchanged

#### Scenario: Closed above the private limit
- **GIVEN** a public party with 12 members that the leader closes
- **WHEN** B is invited to it
- **THEN** the service answers 409 `party-full` and the 12 members stay

### Requirement: Leaving and leadership
When a member leaves, is kicked or disconnects, the service SHALL remove them from the party; when that member was the leader, the service SHALL make the longest-standing remaining member leader; when no member remains, the service SHALL disband the party. A party left with one member stays open as `keep-parties-after-members-leave` specifies. A change of leader SHALL clear the public flag of the party.

#### Scenario: Leader leaves
- **GIVEN** a party with leader A and members B (joined first) and C
- **WHEN** A leaves
- **THEN** B is leader and the party has members B and C

#### Scenario: Only member leaves
- **GIVEN** a party whose only member is A
- **WHEN** A leaves
- **THEN** the party is disbanded with reason `last-member-left` and A is in no party

#### Scenario: Leader leaves a public party
- **GIVEN** a public party of A, B and C
- **WHEN** A leaves
- **THEN** B is leader, the party is not public, and members B and C stay

#### Scenario: Promotion closes a public party
- **GIVEN** a public party led by A with member B
- **WHEN** A promotes B
- **THEN** B is leader and the party is not public
