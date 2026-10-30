# Spec Delta

## Purpose
Event model for join requests and their answers.

## ADDED Requirements

### Requirement: Join request events
When a player requests a join to a private party, the service SHALL publish `party.join-requested` with the requester's id and name and the expiry time. When the leader answers the request, the service SHALL publish `party.join-request-answered` with the requester's id, the requester's name and the outcome `accepted` or `denied`. Both events carry the party's members, the leader and the party version, as the other party events do. Clients SHALL ignore event types they do not know.

#### Scenario: Leader is prompted
- **WHEN** B requests a join to private party P led by A
- **THEN** a `party.join-requested` event with B's id, B's name and the expiry is published, and the leader's plugin prompts A

#### Scenario: Requester is told the answer
- **GIVEN** B holds a request to P
- **WHEN** A rejects it
- **THEN** a `party.join-request-answered` event with outcome `denied` names B as requester

#### Scenario: Accepted request
- **GIVEN** B holds a request to P
- **WHEN** A accepts it
- **THEN** a `party.join-request-answered` event with outcome `accepted` and a `party.joined` event are published

#### Scenario: Expiry publishes nothing
- **GIVEN** B's request to P expires unanswered
- **WHEN** the expiry passes
- **THEN** no event is published for it

### Requirement: Public join event
A join to a public party SHALL publish `party.joined` like an accepted invite does, so the members learn of the new member.

#### Scenario: Public join event
- **GIVEN** public party P with leader A
- **WHEN** B joins P directly
- **THEN** a `party.joined` event naming B is published
