# Spec Delta

## Purpose
The proxy side: commands, messages, presence reporting and the event consumer.

## ADDED Requirements

### Requirement: Service authentication
Where client credentials are configured, the plugin SHALL obtain an app-only token from Entra ID with the client-credentials grant, reuse it until shortly before it expires, send it as a bearer token on every request and on the event stream, and after a 401 fetch a new token and repeat the request once.

#### Scenario: Token renewed
- **GIVEN** a cached token that the service rejects with 401
- **WHEN** the plugin sends a command
- **THEN** it fetches a new token and repeats the command once

#### Scenario: Secret not logged
- **WHEN** the token request fails
- **THEN** the log names the Entra error but not the client secret

### Requirement: Safe retry of commands
The plugin SHALL send every command `POST` with a new `Idempotency-Key`, and when the request fails with a timeout or I/O error, SHALL repeat it once with the same key.

#### Scenario: Timeout on chat
- **GIVEN** a party chat request times out
- **WHEN** the plugin repeats it with the same key
- **THEN** the chat line appears once
