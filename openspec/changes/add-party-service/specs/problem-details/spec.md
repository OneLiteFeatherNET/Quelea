# Spec Delta

## Purpose
The error contract of the HTTP API.

## ADDED Requirements

### Requirement: Problem Details for every error
The service SHALL answer every 4xx and 5xx response with an RFC 9457 body of media type `application/problem+json` containing `type`, `title`, `status`, `detail`, `instance`, a stable `code` and the `traceId` of the request.

#### Scenario: Domain error
- **WHEN** a non-leader kicks a member
- **THEN** the response is 403 with `code` `not-leader` and `type` ending in `#not-leader`

#### Scenario: Validation error
- **WHEN** a chat message is empty
- **THEN** the response is 400 with `code` `validation-failed` and an `errors` entry naming the field

#### Scenario: Unexpected error
- **WHEN** an unexpected exception occurs
- **THEN** the response is 500 with `code` `internal-error` and no exception message or stack trace

### Requirement: Documented codes
Every `code` the service can return SHALL have a section in `docs/problems.md` that its `type` URI points to.

#### Scenario: Type resolves
- **WHEN** a client follows the `type` URI of any problem
- **THEN** it lands on the section describing that code
