# Design

## Context

Builds on `add-party-service` and `add-party-lifecycle`. Blackhole already authenticates its Velocity plugin and Discord
bot against Entra ID with `micronaut-security-jwt`; its validators, start-up guards, test token issuer and the plugin's
`ClientCredentialsTokenProvider` are proven and tested. Quelea copies that pattern, cut down to app-only tokens and one
role.

## Goals / Non-Goals

Goals: only registered clients can call Quelea; stale or repeated writes cannot do damage; local development and tests
never reach Entra. Non-goals: user logins, fine-grained permissions, a shared auth library across repositories (worth it
once a third service copies the same code).

## Decisions

### 1. Token validation (backend)

`micronaut-security` + `micronaut-security-jwt`, `micronaut.security.authentication: bearer`.

- Signature: JWKS from `QUELEA_AUTH_JWKS_URL` (`https://login.microsoftonline.com/<tenant>/discovery/v2.0/keys`),
  only `RS256` accepted (algorithm pinned before any key is read; `none` and HMAC refused).
- Claims: `iss` = `QUELEA_AUTH_ISSUER` (`https://login.microsoftonline.com/<tenant>/v2.0`), `aud` =
  `QUELEA_AUTH_AUDIENCE` (the API app's client id, a GUID), `tid` = `QUELEA_AUTH_TENANT_ID`, `exp` required and `nbf`
  optional with a bounded clock skew (`QUELEA_AUTH_CLOCK_SKEW`, default 60 s, at most 60 s).
- App-only only: a token with `scp` (delegated, a user) is refused with 403.
- Roles from the `roles` claim; every route carries `@Secured("Quelea.Client")`, except anonymous `/health/**`,
  `/swagger/**`, `/swagger-ui/**` (and `/prometheus` until `export-metrics-via-otlp` removes it). A test asserts that
  every route has a rule.
- Start-up guards (outside the `dev` and `test` environments): refuse to start with an http or localhost issuer, a
  non-GUID audience or a missing tenant id.
- Local development: the `dev` environment disables security (`micronaut.security.enabled: false`) so `docker compose`
  plus `./gradlew :backend:run` keeps working; the guard refuses `enabled: false` outside `dev` and `test`.
- Tests: a `TestJwtIssuer` (RSA key per instance, in-memory JWKS) signs tokens, as in Blackhole. No test reaches Entra.

Rejected: Blackhole's additional `azp` allowlist per role (with a single role, the app-role assignment with admin consent
already is the allowlist); validating tokens in Envoy Gateway with a `SecurityPolicy` (the service must not trust the
network; the gateway may additionally do it later); mTLS with step-ca (certificates would have to be issued to CloudNet
services by hand, and Entra is already how services authenticate in the network).

### 2. Authentication failures

401 `unauthorized` (missing, malformed, expired, wrong issuer/audience/tenant, bad signature) with
`WWW-Authenticate: Bearer` and, when a token was sent, `error="invalid_token"` (RFC 6750 §3). 403 `forbidden` (valid
token without `Quelea.Client`, or a delegated token). Both are Problem Details from the existing handler; the detail
never echoes the token.

### 3. Streams and token lifetime

The event controller reads `exp` of the authenticated token and completes the stream at that instant. The client
reconnects with a fresh token; the snapshot after the reconnect restores state (same path as a network error). The log
line of an opened stream names the client label and the token's `azp`.

### 4. Party version and conditional requests

`Party` gains `version` (long, 1 on create, +1 on every save). The JSON body and every event carry it.

- `GET /v1/parties/{id}` and `GET /v1/players/{id}/party` send `ETag: "<version>"`. `If-None-Match` with the current
  tag answers 304 without body.
- `kick`, `promote`, `disband`, `warp` (`POST /v1/parties/{partyId}/...`) accept `If-Match`. The comparison is strong
  (RFC 9110 §13.1.1) and happens under the party lock; `*` matches any existing party; a mismatch answers 412
  `precondition-failed` and changes nothing. Without `If-Match` the command runs unconditionally, so existing clients
  are unaffected.
- Responses that return a party send its new `ETag`.

Rejected: requiring `If-Match` (428, RFC 6585) — the proxy executes commands typed by players and has no earlier read to
compare against, so a mandatory precondition would only add a read before every command; `If-Match` on every write —
`invite`, `accept`, `leave` and `chat` do not depend on what the client saw.

### 5. Idempotency-Key

A server filter on every `POST` under `/v1`:

- Header absent: the request runs as before. Present: 1 to 255 visible ASCII characters, otherwise 400
  `validation-failed`.
- Storage key `idem:{azp}:{sha256(key)}`, so two clients never collide. The fingerprint is `method + path +
  sha256(body)`.
- First request: `SET NX` of an in-progress marker with TTL `QUELEA_IDEMPOTENCY_LOCK` (default `10s`); then execute.
  A 2xx response is stored (status, `Content-Type`, `ETag`, body) with TTL `QUELEA_IDEMPOTENCY_TTL` (default `24h`);
  any other outcome deletes the marker, so the client may retry.
- Same key, same fingerprint, completed: replay the stored response without executing.
- Same key, different fingerprint: 422 `idempotency-key-reused`.
- Same key while the marker is in progress: 409 `request-in-progress` with `Retry-After: 1`.

Rejected: storing error responses (an error is cheap to repeat and may succeed on retry); making the header mandatory
(the draft allows optional use and the 0.1.0 plugin does not send it); idempotency per endpoint by design only (`chat`
and `warp` cannot be made idempotent without a client-chosen id, which is exactly the key).

### 6. Plugin

- `ClientCredentialsTokenProvider` ported from Blackhole (form `grant_type=client_credentials`, scope
  `api://<api-client-id>/.default`, cache until `expires_in − 60 s`, one request for concurrent callers, error messages
  never contain the secret), Gson instead of Jackson, `java.net.http` and an injected `Clock`.
- Configuration: `tenant-id`, `client-id`, `client-secret`, `auth-scope` in `config.properties`, overridden by
  `QUELEA_TENANT_ID`, `QUELEA_CLIENT_ID`, `QUELEA_CLIENT_SECRET`, `QUELEA_AUTH_SCOPE`. Without credentials the plugin
  sends no token and logs one warning at start-up (works only against a `dev` backend).
- `Authorization: Bearer` on every request and on the stream. A 401 clears the cached token, fetches a new one and
  repeats the request once.
- Every command `POST` gets a fresh `Idempotency-Key` (UUID); after an `IOException` or timeout the gateway retries
  once with the same key. A replayed or 409 answer is handled like the original outcome.

### 7. API documentation

`micronaut-openapi` views: Swagger UI at `/swagger-ui` over the generated `quelea.yml`, with the bearer security scheme
declared so "Authorize" works.

### 8. Entra registrations (`docs/entra-setup.md`)

- **Quelea API**: `requestedAccessTokenVersion: 2`, App ID URI `api://<client-id>`, app role `Quelea.Client` (allowed
  member types: Applications).
- **Quelea Velocity** (and later **Quelea Lobby**): client-credentials app with a secret, granted `Quelea.Client` with
  admin consent.
- A helper script `docs/entra-setup.sh` (Azure CLI) creates both and prints the values for Kubernetes-FLUX and CloudNet.

### 9. Rollout order

1. Create the Entra registrations.
2. Release; install the new plugin with credentials in the CloudNet proxy template. The 0.1.0 backend ignores the
   header, so this is safe.
3. Update the backend in Kubernetes-FLUX with the `QUELEA_AUTH_*` values. From now on authentication is enforced.

### 10. Project principle 5

`openspec/config.yaml` principle 5 changes from "No auth layer" to "Every client authenticates with an Entra ID app-only
token carrying `Quelea.Client`; health and API docs stay anonymous".

## Risks / Trade-offs

- **Entra outage**: new tokens cannot be fetched; cached tokens keep working until they expire (about an hour). The
  backend caches the JWKS, so validation continues.
- **Client secret in CloudNet**: rotation is manual; Entra secrets live at most two years. Accepted.
- **One role for all clients**: a compromised client can act for any player. Accepted until a second kind of client with
  fewer needs exists.
