# Spec Delta

## Purpose
Safe retries of writes with `Idempotency-Key`.

## ADDED Requirements

### Requirement: Replay of completed requests
Where a `POST` request carries `Idempotency-Key`, the service SHALL store a successful response under the client and key for the configured time, and SHALL answer a later request with the same client, key, method, path and body with the stored response without executing it again.

#### Scenario: Chat retried after a timeout
- **GIVEN** a chat request with key K succeeded but the client did not receive the answer
- **WHEN** the client sends the same request with key K again
- **THEN** the service answers with the stored response and the chat line is published once

#### Scenario: Failed request may be retried
- **GIVEN** a request with key K answered 409 `party-full`
- **WHEN** the client sends it again with key K after a member left
- **THEN** the service executes it again

### Requirement: Key misuse
If a request reuses a stored key with a different method, path or body, then the service SHALL answer 422 `idempotency-key-reused`; if the first request with that key is still running, then the service SHALL answer 409 `request-in-progress` with `Retry-After`.

#### Scenario: Different body
- **WHEN** a client sends key K first with message "hi" and then with message "bye"
- **THEN** the second request is answered 422 `idempotency-key-reused`

#### Scenario: Concurrent duplicate
- **WHEN** two requests with key K arrive while the first is still executing
- **THEN** the second is answered 409 `request-in-progress` with `Retry-After: 1`

### Requirement: Keys are per client
The service SHALL keep the keys of different authenticated clients apart.

#### Scenario: Same key, two clients
- **WHEN** clients A and B both send key K with different requests
- **THEN** both requests are executed
