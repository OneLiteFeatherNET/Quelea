# Spec Delta

## Purpose
The party follows its leader onto the same server.

## MODIFIED Requirements

### Requirement: Warp on leader move
When the proxy sees a party leader connect from one server to another, the proxy SHALL request a warp of the party to the new server's name and SHALL name the server the leader left as the warp's origin.

#### Scenario: Leader joins a game from the lobby
- **GIVEN** A leads a party with B, both on `Lobby-1`
- **WHEN** A is connected to `Bounce-2`
- **THEN** B is connected to `Bounce-2`

#### Scenario: Leader returns to the lobby
- **GIVEN** A leads a party with B, both on `Bounce-2`
- **WHEN** A is connected to `Lobby-2`
- **THEN** B is connected to `Lobby-2`

#### Scenario: First server after login
- **WHEN** the leader logs in and is connected to `Lobby-1`
- **THEN** no warp is requested

### Requirement: Who follows
When a proxy receives `party.warp`, it SHALL connect each of its local members except the leader who is not already on the target server to that exact server if follow mode is `always`, or if the member is on the warp's origin server, or if the member is on a lobby server.

#### Scenario: Member in another game
- **GIVEN** follow mode `lobby` and member C is on `Tamias-1`
- **WHEN** the leader moves from `Bounce-2` to `Lobby-2`
- **THEN** C stays on `Tamias-1`

#### Scenario: Other lobby instance
- **GIVEN** member B is on `Lobby-1`
- **WHEN** the leader moves from `Bounce-2` to `Lobby-2`
- **THEN** B is connected to `Lobby-2`

#### Scenario: Target full
- **WHEN** a member's connection to the target is refused
- **THEN** the member stays where they are and is told why
