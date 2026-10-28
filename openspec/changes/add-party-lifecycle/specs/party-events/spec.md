# Spec Delta

## Purpose
Event model and real-time delivery to the clients of the event stream.

## ADDED Requirements

### Requirement: Disband reason
When the service disbands a party, the `party.disbanded` event SHALL carry `reason` in its payload with the value `leader`, `last-member-left` or `expired-alone`.

#### Scenario: Leader disbands
- **WHEN** the leader disbands the party
- **THEN** the `party.disbanded` payload has `reason` `leader`

### Requirement: Bounded delivery per stream
The service SHALL hold at most the configured number of undelivered events per open stream; if a stream's buffer overflows, then the service SHALL close that stream, count the disconnect with reason `overflow`, and keep delivering to every other stream.

#### Scenario: Stalled client
- **GIVEN** client S stopped reading its stream and client F reads normally
- **WHEN** more events are published than S's buffer holds
- **THEN** S's stream is closed and F receives every event

#### Scenario: Missed keep-alive
- **GIVEN** a stream whose client is momentarily not reading
- **WHEN** a keep-alive comment cannot be written
- **THEN** the comment is dropped and the stream stays open

### Requirement: Stream for any client
The service SHALL accept `GET /v1/events` with an optional `clientId` label or, for compatibility, `proxyId`, and SHALL deliver every event to every open stream regardless of the label.

#### Scenario: Lobby server subscribes
- **WHEN** a client opens `GET /v1/events?clientId=lobby-1`
- **THEN** it receives the same events as every proxy
