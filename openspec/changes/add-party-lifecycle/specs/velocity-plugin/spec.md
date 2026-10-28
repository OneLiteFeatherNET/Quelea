# Spec Delta

## Purpose
The proxy side: commands, messages, presence reporting and the event consumer.

## ADDED Requirements

### Requirement: Expiry messages
When the plugin receives `party.expiring` for a party whose leader is a local player, it SHALL tell the leader in their language how many minutes remain and how to invite someone; when it receives `party.disbanded` with reason `expired-alone`, it SHALL tell the former leader that the party was closed because nobody joined.

#### Scenario: Warning shown
- **GIVEN** a local leader whose party expires in 5 minutes
- **WHEN** `party.expiring` arrives
- **THEN** the leader reads that the party closes in 5 minutes and that `/party invite` adds someone

#### Scenario: Unknown reason
- **WHEN** `party.disbanded` arrives with a reason the plugin does not know
- **THEN** the former members see the generic disband message
