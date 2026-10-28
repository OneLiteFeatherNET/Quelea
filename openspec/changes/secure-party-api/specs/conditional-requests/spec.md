# Spec Delta

## Purpose
Party versions and HTTP conditional requests.

## ADDED Requirements

### Requirement: Party version
The service SHALL give every party a version that is 1 on creation and increases by one with every change, and SHALL include it in party responses and in every party event.

#### Scenario: Join increases the version
- **GIVEN** a party with version 3
- **WHEN** a player joins it
- **THEN** the party has version 4 and the `party.joined` event carries 4

### Requirement: Entity tag on reads
The service SHALL send the party version as a strong `ETag` on `GET /v1/parties/{id}` and `GET /v1/players/{id}/party`, and SHALL answer 304 without body when `If-None-Match` names the current tag.

#### Scenario: Unchanged party
- **GIVEN** a client holds `ETag: "4"`
- **WHEN** it reads the party with `If-None-Match: "4"` and the party is still at version 4
- **THEN** the service answers 304

### Requirement: Preconditions on leader commands
Where a `kick`, `promote`, `disband` or `warp` request carries `If-Match`, the service SHALL execute it only if a listed tag strongly matches the current version or the header is `*`, and SHALL otherwise answer 412 `precondition-failed` without changing the party.

#### Scenario: Party changed meanwhile
- **GIVEN** the leader read the party at version 4 and a member left (version 5)
- **WHEN** the leader kicks with `If-Match: "4"`
- **THEN** the service answers 412 `precondition-failed` and nobody is kicked

#### Scenario: No precondition
- **WHEN** the leader kicks without `If-Match`
- **THEN** the kick runs as before
