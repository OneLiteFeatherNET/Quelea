# Spec Delta

## Purpose
The error contract.

## ADDED Requirements

### Requirement: Security and request-condition problems
The service SHALL answer authentication, authorisation, precondition and idempotency failures as Problem Details with the codes `unauthorized` (401), `forbidden` (403), `precondition-failed` (412), `idempotency-key-reused` (422) and `request-in-progress` (409), each documented in `docs/problems.md`.

#### Scenario: Forbidden
- **WHEN** a token without `Quelea.Client` is sent
- **THEN** the body is `application/problem+json` with `code` `forbidden` and a `type` pointing at its section in `docs/problems.md`
