# Spec Delta

## Purpose
Traces, metrics, logs and health.

## ADDED Requirements

### Requirement: Party statistics
The service SHALL count created parties, count disbanded parties by reason, record the lifetime of every disbanded party by reason, record the party size after every join, and report the number of players online, without player ids or names in any metric.

#### Scenario: Party expires
- **GIVEN** a party created 30 minutes ago that nobody joined
- **WHEN** it expires
- **THEN** `quelea.parties.disbanded` with `reason="expired-alone"` increases by one and `quelea.party.lifetime` records 30 minutes for that reason

#### Scenario: Join recorded
- **WHEN** a third player joins a party
- **THEN** `quelea.party.size` records 3

#### Scenario: Players online
- **GIVEN** proxy P1 hosts 3 players and proxy P2 hosts 2
- **WHEN** metrics are exported
- **THEN** `quelea.players.online` is 5
