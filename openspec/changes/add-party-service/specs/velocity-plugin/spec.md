# Spec Delta

## Purpose
The proxy side: commands, messages, presence reporting and the event consumer.

## ADDED Requirements

### Requirement: Commands
The plugin SHALL register `/party` (alias `/p`) with the sub-commands `create`, `invite <player>`, `accept [player]`, `deny [player]`, `leave`, `kick <player>`, `promote <player>`, `disband`, `list` and `warp`, and `/pc <message>` for party chat, run every service call off the proxy's event threads, and answer every outcome (including each problem `code`) with a translated message.

#### Scenario: Accept without name
- **GIVEN** the player holds exactly one invite
- **WHEN** they run `/party accept`
- **THEN** that invite is accepted

#### Scenario: Service down
- **WHEN** the service is unreachable
- **THEN** the player is told that the party service is unavailable, and the proxy keeps working

### Requirement: Messages
The plugin SHALL tell affected local players about every party event in English or German according to their client locale, and SHALL show invites with clickable accept and deny actions.

#### Scenario: Invite message
- **WHEN** a local player receives `party.invited`
- **THEN** they see who invited them with clickable [Accept] and [Deny]

### Requirement: Presence and stream
The plugin SHALL report logins and disconnects, send a heartbeat every 15 s, keep one event stream open with exponential backoff up to 30 s between reconnects, and send a full player snapshot after every (re)connect.

#### Scenario: Service restart
- **WHEN** the stream breaks and the service is back after 5 s
- **THEN** the plugin reconnects and sends a snapshot of its players
