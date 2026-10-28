# Tasks

## 1. Authentication (backend)

- [ ] 1.1 Dependencies `micronaut-security`, `micronaut-security-jwt`, security annotation processor.
- [ ] 1.2 Configuration `QUELEA_AUTH_ISSUER`, `QUELEA_AUTH_AUDIENCE`, `QUELEA_AUTH_TENANT_ID`, `QUELEA_AUTH_JWKS_URL`,
      `QUELEA_AUTH_CLOCK_SKEW`; `dev` environment with security disabled.
- [ ] 1.3 RS256-pinned JWKS signature, time-claims validator with bounded skew, tenant validator, app-only check, start-up
      guard (ported from Blackhole).
- [ ] 1.4 `@Secured("Quelea.Client")` on every route, anonymous health and docs; a test that every route has a rule.
- [ ] 1.5 401/403 Problem Details with `WWW-Authenticate`; codes in `PartyProblem` and `docs/problems.md`.
- [ ] 1.6 `TestJwtIssuer` + test JWKS signature; authentication tests (none, expired, wrong issuer/audience/tenant,
      `alg: none`, HMAC key confusion, missing role, delegated token, open health).
- [ ] 1.7 Event stream completes at the token's `exp`; log line with client label and `azp`.

## 2. Conditional requests (backend)

- [ ] 2.1 `Party.version` (store, JSON, events), incremented on every save.
- [ ] 2.2 `ETag` on party reads, `If-None-Match` → 304.
- [ ] 2.3 `If-Match` on `kick`, `promote`, `disband`, `warp`, compared under the party lock; 412 `precondition-failed`.
- [ ] 2.4 Unit and HTTP tests.

## 3. Idempotency (backend)

- [ ] 3.1 Port and Redis adapter for idempotency records (`idem:{azp}:{sha256(key)}`), in-memory fake.
- [ ] 3.2 Server filter on `POST /v1/**`: validation, in-progress marker, replay, 422, 409 with `Retry-After`,
      `QUELEA_IDEMPOTENCY_TTL`, `QUELEA_IDEMPOTENCY_LOCK`.
- [ ] 3.3 Tests: replay without second execution, different body, concurrent duplicate, failures not stored, keys per
      client; Redis integration test.

## 4. API documentation

- [ ] 4.1 Swagger UI at `/swagger-ui` with the bearer security scheme.

## 5. Velocity plugin

- [ ] 5.1 `ClientCredentialsTokenProvider` (port from Blackhole, Gson, injected `Clock`) and tests with a fake transport.
- [ ] 5.2 Configuration keys and environment variables; warning when credentials are missing.
- [ ] 5.3 Bearer header on requests and the stream; one renewal after 401.
- [ ] 5.4 `Idempotency-Key` per command and one retry with the same key after a timeout or I/O error.
- [ ] 5.5 Gateway tests with a fake HTTP server or transport.

## 6. Documentation

- [ ] 6.1 `docs/entra-setup.md` and `docs/entra-setup.sh`.
- [ ] 6.2 README: configuration tables for backend and plugin, rollout order.

## 7. Pull request

- [ ] 7.1 Open the code pull request `feat(api): authenticate clients with entra id and make writes safe to retry`.
