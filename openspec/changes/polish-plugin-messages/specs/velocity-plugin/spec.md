# Spec Delta

## Purpose
The proxy side: commands, messages, presence reporting and the event consumer.

## ADDED Requirements

### Requirement: Clickable help
The plugin SHALL render every `/party` help line so that clicking it suggests the command in the chat input and hovering explains it in the player's language.

#### Scenario: Click on invite
- **WHEN** a player clicks the help line of `/party invite`
- **THEN** their chat input contains `/party invite `

### Requirement: Message palette
The plugin SHALL colour messages by meaning: the brand gradient for the prefix and headers, green for successful actions, red for errors and problems, amber for hints, and gold with a star for the party leader.

#### Scenario: Problem shown in red
- **WHEN** a command fails with `party-full`
- **THEN** the player reads the translated problem text in red after the party prefix

### Requirement: Translations from bundles and overrides
The plugin SHALL choose the message bundle for the client locale by language and country, then language, then English, SHALL merge `messages_<tag>.properties` files from `plugins/quelea/lang/` over the built-in bundles key by key, and SHALL fall back to the English text for any key missing in the chosen bundle or whose template cannot be parsed.

#### Scenario: Operator adds French
- **GIVEN** `plugins/quelea/lang/messages_fr.properties` translates only `party.joined`
- **WHEN** a player with locale `fr_FR` sees a join and a disband
- **THEN** the join is French and the disband is English

#### Scenario: Override recolours a message
- **GIVEN** `plugins/quelea/lang/messages_de.properties` overrides `prefix`
- **WHEN** a German player receives any message
- **THEN** it starts with the overridden prefix and the other texts are the built-in German ones
