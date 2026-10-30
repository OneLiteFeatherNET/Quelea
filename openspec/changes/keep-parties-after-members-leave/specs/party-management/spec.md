# Spec Delta

## Purpose
Party lifecycle, membership rules and invites.

## MODIFIED Requirements

### Requirement: Lonely-party expiry
While a party has exactly one member, the service SHALL count its alone time from the moment that alone period began, which
is the party's creation or the departure that left it with one member. The service SHALL publish `party.expiring` once the
configured warning time before the end of the configured alone timeout is reached, and SHALL disband the party with the reason
`expired-alone` when the alone timeout has passed, at most one expiry interval late. A second member joining the party SHALL
end the alone period.

#### Scenario: Nobody joins
- **GIVEN** player A created a party 30 minutes ago and nobody accepted an invite
- **WHEN** the expiry job runs
- **THEN** the party is disbanded, A is in no party and a `party.disbanded` event with reason `expired-alone` is published

#### Scenario: Warning before expiry
- **GIVEN** player A created a party 25 minutes ago and nobody joined
- **WHEN** the expiry job runs
- **THEN** a `party.expiring` event with the expiry time is published exactly once

#### Scenario: Somebody joins in time
- **GIVEN** player A created a party 20 minutes ago
- **WHEN** player B accepts A's invite
- **THEN** the party is no longer subject to expiry

#### Scenario: Several replicas
- **GIVEN** two replicas run the expiry job at the same moment
- **WHEN** a party is due
- **THEN** it is disbanded once and one `party.disbanded` event is published

## ADDED Requirements

### Requirement: Party stays open after its other members leave
When a leave or a kick leaves a party with exactly one member, the service SHALL keep the party with its id, SHALL keep that
member in it, SHALL make that member leader when the departed member was the leader, and SHALL start a new alone period at the
moment of the departure. The service SHALL NOT disband such a party because of the departure.

#### Scenario: Drops to one and stays open
- **GIVEN** player A leads a party with members A and B
- **WHEN** B leaves the party
- **THEN** the party still exists with the same id, A is its only member and its leader, and no `party.disbanded` event is published

#### Scenario: Leader leaves and the other member takes over
- **GIVEN** player A leads a party with members A and B
- **WHEN** A leaves the party
- **THEN** the party still exists with B as its only member and its leader, and a `party.leader-changed` event names B

#### Scenario: Expiry after the timeout
- **GIVEN** B left a party of A and B at 10:00, and nobody joined since
- **WHEN** the expiry job runs at or after 10:30
- **THEN** the party is disbanded with reason `expired-alone` and A is in no party

#### Scenario: Warning at the right time
- **GIVEN** B left a party of A and B at 10:00, and nobody joined since
- **WHEN** the expiry job runs at 10:25
- **THEN** a `party.expiring` event is published exactly once, with `expiresAt` 10:30

#### Scenario: Second member cancels
- **GIVEN** B left a party of A and B at 10:00
- **WHEN** player C accepts A's invite at 10:10
- **THEN** the party is no longer subject to expiry, and no `party.expiring` event is published at 10:25

#### Scenario: Alone again restarts the timer
- **GIVEN** A and B are in a party; B leaves at 10:10; C joins at 10:12; C leaves at 10:20
- **WHEN** the expiry job runs at 10:30
- **THEN** the party is still open with A as its only member, and it is disbanded with reason `expired-alone` at or after 10:50, not at 10:40

#### Scenario: Zero members disbands at once
- **GIVEN** player A is the only member of a party
- **WHEN** A leaves the party
- **THEN** the party is disbanded immediately with reason `last-member-left`, and no alone period is started

#### Scenario: Kick that leaves one member behaves like a leave
- **GIVEN** player A leads a party with members A and B
- **WHEN** A kicks B
- **THEN** the party stays open with A as its only member, and the alone period starts at the kick

#### Scenario: Legacy party without the stored alone time
- **GIVEN** a party was stored by an older backend with creation time 09:00 and no alone-since value
- **WHEN** the expiry job runs at 09:30
- **THEN** the party is disbanded with reason `expired-alone`, as it would have been before this change
