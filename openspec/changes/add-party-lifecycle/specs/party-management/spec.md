# Spec Delta

## Purpose
Party lifecycle, membership rules and invites.

## ADDED Requirements

### Requirement: Lonely-party expiry
While a party has had only one member since its creation, the service SHALL publish `party.expiring` once the configured warning time before the configured alone timeout is reached, and SHALL disband the party with the reason `expired-alone` when the alone timeout has passed, at most one expiry interval late.

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
