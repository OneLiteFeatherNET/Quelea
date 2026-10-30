# Spec Delta

## Purpose
The proxy side: the new commands, the permission check, the prompts and the messages.

## ADDED Requirements

### Requirement: Public party command
The plugin SHALL offer `/party public on` and `/party public off`. `on` SHALL be sent to the service only when the player holds the Velocity permission `quelea.party.public`; otherwise the proxy SHALL refuse it and the service SHALL NOT be called. `off` SHALL be sent for any leader, with no permission check, so a leader who lost the permission can close the party. The check is made by the plugin because the service has no notion of Minecraft permissions.

#### Scenario: Opening without the permission
- **GIVEN** a leader without `quelea.party.public`
- **WHEN** the leader runs `/party public on`
- **THEN** the proxy answers that the player may not open a party, and no request reaches the service

#### Scenario: Opening with the permission
- **GIVEN** a leader with `quelea.party.public`
- **WHEN** the leader runs `/party public on`
- **THEN** the service is asked to open the party and the leader reads that it is open

#### Scenario: Closing without the permission
- **GIVEN** a leader without `quelea.party.public` who leads an open party
- **WHEN** the leader runs `/party public off`
- **THEN** the service is asked to close the party and the leader reads that it is closed

### Requirement: Join and request commands
The plugin SHALL offer `/party join <player>` and `/party request <player>` to players who are not in a party. Their answers SHALL be shown in the player's language. A request to a public party answers that the player joined. A join to a private party answers with the `party-not-public` text, which names `/party request`.

#### Scenario: Request sent
- **WHEN** B runs `/party request a` for a private party
- **THEN** B reads that the request was sent and how long it stays open

#### Scenario: Join refused for a private party
- **WHEN** B runs `/party join a` for a private party
- **THEN** B reads the `party-not-public` text that names `/party request`

### Requirement: Approve and reject commands
The plugin SHALL offer `/party approve <player>` and `/party reject <player>` to the leader. The leader's prompt for a join request SHALL contain a clickable `[Approve]` that runs `/party approve <name>` and a clickable `[Reject]` that runs `/party reject <name>`.

#### Scenario: Leader approves from the prompt
- **GIVEN** the leader received a prompt for B
- **WHEN** the leader clicks `[Approve]`
- **THEN** the service is asked to accept B's request and B becomes a member

### Requirement: Join request messages
When the plugin receives `party.join-requested` for a party whose leader is a local player, it SHALL prompt the leader with the requester's name and the two buttons. When it receives `party.join-request-answered`, it SHALL tell the requester, if local, that the request was accepted or denied.

#### Scenario: Leader prompt
- **WHEN** `party.join-requested` arrives for a local leader
- **THEN** the leader reads that B asks to join, with `[Approve]` and `[Reject]`

#### Scenario: Requester told of a denial
- **WHEN** `party.join-request-answered` arrives with outcome `denied` for a local requester
- **THEN** the requester reads that the party declined the request

#### Scenario: Unknown event type
- **WHEN** an event type the plugin does not know arrives
- **THEN** the plugin ignores it

### Requirement: Help lists the new commands
The `/party` help SHALL list `public`, `request`, `join`, `approve` and `reject` with a hover text in each language, and the English and German bundles SHALL carry the same keys.

#### Scenario: Help shows the new lines
- **WHEN** a player runs `/party` without arguments
- **THEN** the help lists the new commands in the player's language
