# Spec Delta

## Purpose
Event model and real-time delivery to the clients of the event stream.

## MODIFIED Requirements

### Requirement: Disband reason
When the service disbands a party, the `party.disbanded` event SHALL carry `reason` in its payload with the value `leader`,
`last-member-left` or `expired-alone`. The value `last-member-left` SHALL be used only when the departure left no member in
the party. A party that drops to one member is not disbanded by the departure.

#### Scenario: Leader disbands
- **WHEN** the leader disbands the party
- **THEN** the `party.disbanded` payload has `reason` `leader`

#### Scenario: Last member leaves
- **GIVEN** player A is the only member of a party
- **WHEN** A leaves
- **THEN** the `party.disbanded` payload has `reason` `last-member-left`

#### Scenario: Alone timeout ends
- **GIVEN** a party has had one member since 10:00
- **WHEN** the alone timeout ends at 10:30
- **THEN** the `party.disbanded` payload has `reason` `expired-alone`

## ADDED Requirements

### Requirement: Expiry time of party.expiring
The `party.expiring` event SHALL carry `expiresAt`, the moment the alone timeout of the party's current alone period ends. The
alone period starts at the party's creation or at the departure that left the party with one member. For a party stored without
an alone-since value, the alone period starts at its creation.

#### Scenario: Party was left alone
- **GIVEN** a party was created at 09:00 with members A and B, and B left at 10:00
- **WHEN** the warning is due and the alone timeout is 30 minutes
- **THEN** `party.expiring` carries `expiresAt` 10:30, not 09:30

#### Scenario: Legacy party
- **GIVEN** a party was created at 09:00 by an older backend and has no alone-since value
- **WHEN** the warning is due
- **THEN** `party.expiring` carries `expiresAt` 09:30
