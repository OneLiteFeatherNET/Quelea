# Spec Delta

## Purpose
Traces, metrics, logs and health.

## ADDED Requirements

### Requirement: Report backend errors to Sentry
The service SHALL send one Sentry event tagged `component=backend`, with the release and the environment, for every log event at ERROR level and every unhandled exception, when a DSN is configured.

#### Scenario: Unhandled exception reported
- **GIVEN** a DSN is configured
- **WHEN** a request fails with an unhandled exception and the global handler logs it at ERROR
- **THEN** one Sentry event is sent with tag `component=backend`, the release `quelea@<version>` and the configured environment

#### Scenario: Server error logged at ERROR
- **GIVEN** a DSN is configured
- **WHEN** the backend logs an ERROR event
- **THEN** the event reaches Sentry with the trace id of the current request as tag `trace_id`

### Requirement: Keep expected answers out of Sentry
The service SHALL NOT send events for expected domain answers, meaning 4xx Problem Details such as party not found, conflicts, cannot-target-self, unauthorised, forbidden and precondition failed.

#### Scenario: Party not found
- **GIVEN** a DSN is configured
- **WHEN** a client asks for a party that does not exist and receives 404
- **THEN** no Sentry event is sent

### Requirement: No personal data
The service SHALL NOT send personal data to Sentry: it SHALL NOT send the user IP address, request headers or cookies, and SHALL NOT tag events with player names or UUIDs.

#### Scenario: Request headers removed
- **GIVEN** a DSN is configured
- **WHEN** an error event contains request headers and cookies
- **THEN** the event sent to Sentry contains neither

### Requirement: Error reporting is off without a DSN
The service SHALL NOT create a Sentry client and SHALL NOT send any event when the DSN is blank or unset.

#### Scenario: No DSN configured
- **GIVEN** `QUELEA_SENTRY_DSN` is unset
- **WHEN** the backend logs an ERROR event
- **THEN** no Sentry client is created and no event is sent

#### Scenario: Blank DSN
- **GIVEN** `QUELEA_SENTRY_DSN` is set to an empty value
- **WHEN** the service starts
- **THEN** startup completes without initialising Sentry
