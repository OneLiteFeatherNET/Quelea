# Spec Delta

## Purpose
Velocity proxy plugin: party commands, party messages and event handling.

## ADDED Requirements

### Requirement: Remaining member is told the party stays open
When a `party.left` or `party.kicked` event leaves exactly one member in the party, the plugin SHALL tell that member, in the
locale of the player, that the others left or were removed and that the party closes if nobody joins for a while. With more
members left, the plugin SHALL keep its existing messages.

#### Scenario: Partner leaves
- **GIVEN** a local player A is the only member left after B left
- **WHEN** the plugin handles `party.left`
- **THEN** A receives the `party.left.alone` message in A's locale

#### Scenario: Partner is kicked
- **GIVEN** a local player A is the only member left after the leader kicked B
- **WHEN** the plugin handles `party.kicked`
- **THEN** A receives the `party.left.alone` message in A's locale

#### Scenario: Larger party
- **GIVEN** three members, one of them leaves
- **WHEN** the plugin handles `party.left`
- **THEN** the remaining two receive `party.left` and no `party.left.alone` message is sent

#### Scenario: Older backend behaviour
- **GIVEN** a party that a plugin 0.4.0 receives a `party.left` for with one member left
- **WHEN** the plugin handles it
- **THEN** the remaining member receives the generic `party.left` message and nothing fails
