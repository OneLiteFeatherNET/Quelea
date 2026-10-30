# Spec Delta

## Purpose
The proxy side: commands, messages, presence reporting and the event consumer.

## ADDED Requirements

### Requirement: Report unexpected plugin failures
The plugin SHALL report to Sentry every unexpected exception raised by a command handler, the event handler or a warp, and every HTTP answer with status 5xx or with a status that is not a documented domain problem, tagged `component=velocity` with the `proxy_id` and, where known, the `party_id`.

#### Scenario: Command handler throws
- **GIVEN** a DSN is configured
- **WHEN** a `/party` command handler throws an unexpected exception
- **THEN** one Sentry event is sent with tag `component=velocity` and the player still receives a generic failure message

#### Scenario: Server error answer
- **GIVEN** a DSN is configured
- **WHEN** the service answers an invite request with 500
- **THEN** one Sentry event is sent with the `proxy_id` tag

### Requirement: Report a lasting event-stream outage once
The plugin SHALL report the event stream as failed once when it has stayed disconnected for 5 minutes, and SHALL report a further outage only after the stream has reconnected.

#### Scenario: Outage lasts five minutes
- **GIVEN** a DSN is configured and the event stream is disconnected
- **WHEN** it stays disconnected for 5 minutes
- **THEN** exactly one Sentry event is sent for this outage

#### Scenario: Repeated retries during one outage
- **GIVEN** the event stream has been reported for the current outage
- **WHEN** reconnection attempts keep failing
- **THEN** no further Sentry event is sent

#### Scenario: Second outage after recovery
- **GIVEN** the event stream reconnected after the first outage
- **WHEN** it is disconnected for 5 minutes again
- **THEN** a second Sentry event is sent

### Requirement: Do not report recovered network errors or domain answers
The plugin SHALL NOT report a single network error or timeout that the retry or the reconnect recovers, and SHALL NOT report documented 4xx domain problems. A 401 or 403 is reported only when it remains after the token refresh retry.

#### Scenario: Timeout recovered by retry
- **WHEN** an HTTP request times out once and the retry succeeds
- **THEN** no Sentry event is sent

#### Scenario: Party full
- **WHEN** a join is answered with the documented `party-full` problem
- **THEN** no Sentry event is sent

#### Scenario: Credentials broken
- **WHEN** a request still receives 401 after the token refresh retry
- **THEN** one Sentry event is sent

### Requirement: No player data in error reports
The plugin SHALL NOT send player names, player UUIDs or request headers in error reports.

#### Scenario: Player data absent
- **GIVEN** a DSN is configured
- **WHEN** a command fails for a named player
- **THEN** the Sentry event contains neither the player's name nor their UUID

### Requirement: Error reporting is off without a DSN
The plugin SHALL use a no-op error reporter and SHALL NOT contact Sentry when neither the config key `sentry-dsn` nor the environment variable `QUELEA_SENTRY_DSN` holds a non-blank value.

#### Scenario: No DSN
- **GIVEN** no DSN is configured
- **WHEN** a command handler throws an unexpected exception
- **THEN** no Sentry event is sent and the player still receives the generic failure message

#### Scenario: Environment set without a DSN
- **GIVEN** `sentry-environment` is set to `staging` and no DSN is configured
- **WHEN** the plugin starts
- **THEN** Sentry is not initialised
