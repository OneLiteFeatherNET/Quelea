# Spec Delta

## Purpose
Event model and real-time delivery to proxies across service replicas.

## ADDED Requirements

### Requirement: One event per change
When a party operation succeeds, the service SHALL publish exactly one event carrying its type, the party id, the member list after the change, the leader id, the operation's payload and the W3C trace context of the request.

#### Scenario: Join event
- **WHEN** B accepts A's invite
- **THEN** a `party.joined` event lists A and B as members and names B in its payload

### Requirement: Fan-out across replicas
The service SHALL deliver every event to every open event stream, regardless of which replica handled the request and which replica holds the stream.

#### Scenario: Two replicas
- **GIVEN** proxy P is connected to replica 1
- **WHEN** replica 2 handles an invite
- **THEN** P receives `party.invited`

### Requirement: Event stream
The service SHALL serve `GET /v1/events` as `text/event-stream`, write each event as one SSE message with the event id and type, and send a keep-alive comment at the configured interval while the stream is idle.

#### Scenario: Idle stream
- **GIVEN** an open stream and no party activity
- **WHEN** the keep-alive interval passes
- **THEN** the stream receives a comment line
