# Spec Delta

## Purpose
The party follows its leader onto the same server.

## ADDED Requirements

### Requirement: Warp on leader move
When the proxy sees a party leader connect to a server whose name does not match the lobby pattern, the proxy SHALL request a warp of the party to that server's name.

#### Scenario: Leader joins a game from the lobby
- **GIVEN** A leads a party with B, both on `Lobby-1`
- **WHEN** A is connected to `Bounce-2`
- **THEN** B is connected to `Bounce-2`

#### Scenario: Leader returns to the lobby
- **WHEN** the leader is connected to `Lobby-2`
- **THEN** no warp is requested

### Requirement: Who follows
When a proxy receives `party.warp`, it SHALL connect each of its local members except the leader to the exact target server if the member is not already there and, in follow mode `lobby`, is currently on a lobby server; in follow mode `always` regardless of their server.

#### Scenario: Member in another game
- **GIVEN** follow mode `lobby` and member C is on `Tamias-1`
- **WHEN** the party is warped to `Bounce-2`
- **THEN** C stays on `Tamias-1`

#### Scenario: Target full
- **WHEN** a member's connection to the target is refused
- **THEN** the member stays where they are and is told why

### Requirement: Manual warp
The service SHALL let the leader warp the party to the leader's current server with `/party warp`.

#### Scenario: Missed warp
- **WHEN** the leader runs `/party warp` on `Bounce-2`
- **THEN** members in a lobby are connected to `Bounce-2`
