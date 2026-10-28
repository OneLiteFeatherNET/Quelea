# Spec Delta

## Purpose
Which player is online on which proxy, and cleanup when players or proxies go away.

## ADDED Requirements

### Requirement: Presence updates
The service SHALL record a player as online on a proxy when the proxy reports a login, and SHALL treat a reported disconnect like a leave of the player's party and drop the player's pending invites.

#### Scenario: Disconnect of a member
- **GIVEN** player B is a member of A's party
- **WHEN** B's proxy reports B's disconnect
- **THEN** B is no longer a member and `party.left` is published

### Requirement: Snapshot
When a proxy sends a full player snapshot, the service SHALL make the proxy's player set equal to the snapshot and SHALL treat players that were on the proxy but are missing from the snapshot as disconnected.

#### Scenario: Snapshot after reconnect
- **GIVEN** the service believes A and B are on proxy P
- **WHEN** P sends a snapshot containing only A
- **THEN** B is treated as disconnected

### Requirement: Dead proxies
If a proxy has not sent a heartbeat for longer than the proxy timeout, then the service SHALL treat all its players as disconnected and forget the proxy, with exactly one replica doing so per sweep.

#### Scenario: Proxy crash
- **GIVEN** proxy P with player A stopped sending heartbeats
- **WHEN** the proxy timeout has passed and a sweep runs
- **THEN** A is offline and has left their party
