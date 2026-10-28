# Spec Delta

## Purpose
Party lifecycle, membership rules and invites.

## ADDED Requirements

### Requirement: One party per player
The service SHALL keep every player in at most one party, also when several replicas handle requests for the same player at the same time.

#### Scenario: Concurrent accepts
- **GIVEN** a player holds invites from two parties
- **WHEN** both accepts reach two different replicas at the same moment
- **THEN** exactly one accept succeeds and the other is answered with `already-in-party`

#### Scenario: Create while in a party
- **WHEN** a player who is already in a party creates a party
- **THEN** the service answers 409 `already-in-party`

### Requirement: Invites
When the leader invites a player by name, the service SHALL resolve the name case-insensitively against the network presence, create an invite that expires after the configured invite TTL, and create a party for the inviter first if the inviter has none.

#### Scenario: Invite without party
- **GIVEN** player A has no party and player B is online
- **WHEN** A invites "b"
- **THEN** a party with leader A exists and B holds an invite to it

#### Scenario: Offline target
- **WHEN** the leader invites a name that is not online on any proxy
- **THEN** the service answers 404 `player-not-online`

#### Scenario: Not the leader
- **WHEN** a member who is not the leader invites someone
- **THEN** the service answers 403 `not-leader`

#### Scenario: Expired invite
- **GIVEN** an invite older than the invite TTL
- **WHEN** the target accepts it
- **THEN** the service answers 404 `invite-not-found`

### Requirement: Size limit
If a party already has the configured maximum number of members, then the service SHALL reject invites and accepts for it with 409 `party-full`.

#### Scenario: Full party
- **GIVEN** a party with the maximum number of members
- **WHEN** an invited player accepts
- **THEN** the service answers 409 `party-full` and the party is unchanged

### Requirement: Leaving and leadership
When a member leaves, is kicked or disconnects, the service SHALL remove them from the party; when that member was the leader, the service SHALL make the longest-standing remaining member leader; when one or no member remains, the service SHALL disband the party.

#### Scenario: Leader leaves
- **GIVEN** a party with leader A and members B (joined first) and C
- **WHEN** A leaves
- **THEN** B is leader and the party has members B and C

#### Scenario: Last partner leaves
- **GIVEN** a party of A and B
- **WHEN** B leaves
- **THEN** the party is disbanded and A is in no party

### Requirement: Leader-only commands
The service SHALL accept `kick`, `promote`, `disband` and `warp` only from the party leader and SHALL reject them from anyone else with 403 `not-leader`, and SHALL reject a leader targeting themselves with kick or promote with 400 `cannot-target-self`.

#### Scenario: Promote
- **WHEN** the leader promotes member B
- **THEN** B is leader and the former leader stays a member
