# Spec Delta

## Purpose
Who may call the service and how authentication failures look.

## ADDED Requirements

### Requirement: Bearer token required
The service SHALL accept a request to any route except `/health`, `/swagger` and `/swagger-ui` only with an app-only Entra ID access token that is signed with RS256 by a key from the configured JWKS, names the configured issuer, audience and tenant, has not expired, and carries the app role `Quelea.Client`.

#### Scenario: No token
- **WHEN** a client calls `GET /v1/parties/{id}` without `Authorization`
- **THEN** the service answers 401 `unauthorized` with `WWW-Authenticate: Bearer`

#### Scenario: Expired token
- **WHEN** a client sends a token whose `exp` lies more than the clock skew in the past
- **THEN** the service answers 401 `unauthorized` with `error="invalid_token"` in the challenge

#### Scenario: Missing role
- **WHEN** a client sends a valid token without `Quelea.Client`
- **THEN** the service answers 403 `forbidden`

#### Scenario: Delegated token
- **WHEN** a client sends a token that contains `scp`
- **THEN** the service answers 403 `forbidden`

#### Scenario: Unsigned token
- **WHEN** a client sends a token with `alg` `none` or an HMAC algorithm
- **THEN** the service answers 401 `unauthorized`

#### Scenario: Health stays open
- **WHEN** a client calls `/health` without a token
- **THEN** the service answers with the health status

### Requirement: Unsafe configuration refused
If the service runs outside the `dev` and `test` environments with security disabled, an http or localhost issuer, a non-GUID audience or no tenant id, then the service SHALL refuse to start.

#### Scenario: Security disabled in production
- **WHEN** the service starts without the `dev` or `test` environment and with security disabled
- **THEN** start-up fails with a message naming the setting

### Requirement: Stream ends with the token
When the access token of an open event stream expires, the service SHALL complete that stream.

#### Scenario: Token expires
- **GIVEN** a stream opened with a token that expires at 12:00
- **WHEN** 12:00 passes
- **THEN** the stream is completed by the service
