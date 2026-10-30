# Spec Delta

## Purpose
New documented error codes for join requests, public parties and kick bans.

## ADDED Requirements

### Requirement: Codes for joining a party
The service SHALL answer `party-not-public` (409) when a join targets a private party, `party-banned` (403) when a kicked player joins or requests the party, and `target-not-in-party` (404) when the target of a join or request is in no party.

#### Scenario: Private party join
- **WHEN** a player joins a private party by name
- **THEN** the response is 409 with code `party-not-public` and a message that names `/party request`

#### Scenario: Banned player
- **WHEN** a kicked player joins or requests the party
- **THEN** the response is 403 with code `party-banned`

#### Scenario: Target in no party
- **WHEN** a player requests the party of a target who is in no party
- **THEN** the response is 404 with code `target-not-in-party`

### Requirement: Codes for join requests
The service SHALL answer `join-request-pending` (409) for a duplicate request, `join-request-cooldown` (429, with a `Retry-After` header of the whole seconds left) for a request inside the cooldown, and `join-request-not-found` (404) when the leader answers a request that is not open.

#### Scenario: Duplicate request
- **WHEN** a player requests a party that already holds an open request of theirs
- **THEN** the response is 409 with code `join-request-pending`

#### Scenario: Cooldown carries Retry-After
- **WHEN** the service answers `join-request-cooldown`
- **THEN** the response has a `Retry-After` header with the whole seconds left

#### Scenario: Answer to an expired request
- **WHEN** the leader accepts or rejects a request that has expired
- **THEN** the response is 404 with code `join-request-not-found`

### Requirement: Every new code is documented and translated
Every new code SHALL have a section of its own in `docs/problems.md`, a `problem.<code>` key in the English and the German message bundle, and a row in `MessagesTest`. A code without an English key is reported to Sentry by the plugin as undocumented; with the key present, `join-request-cooldown` (429) and the other new codes are not reported.

#### Scenario: Documented in the docs
- **WHEN** a new code is added to `PartyProblem`
- **THEN** `docs/problems.md` has a `## <code>` section for it

#### Scenario: Translated in both bundles
- **WHEN** a new code is added
- **THEN** `messages_en.properties` and `messages_de.properties` each contain `problem.<code>`

#### Scenario: Cooldown is not reported to Sentry
- **GIVEN** the English bundle has `problem.join-request-cooldown`
- **WHEN** the plugin receives a 429 `join-request-cooldown` answer
- **THEN** `FailureReports` does not send it to Sentry
