# Proposal

**Ships as:** `feat(api)`: one code pull request `feat(api): authenticate clients with entra id and make writes safe to retry`.

## Why

Quelea 0.1.0 trusts everyone who can reach `quelea.apps.onelite.feather`. Any process in the internal network can
invite, kick or disband in anyone's name. More clients are coming (the lobby, tools), and each of them should prove
who it is with the same service-to-service login the network already uses for Blackhole.

Two further problems show up as soon as more than one client acts on the same party or a network hiccup forces a retry:

- A leader's command can act on a party that changed in the meantime (someone else became leader, a member left), and
  the client has no way to say "only if the party is still the one I looked at".
- A request that timed out may have been executed. Retrying it can execute it twice: two chat lines, two warps.

## What Changes

- **Entra ID authentication** (OAuth 2.0 client credentials, RFC 6749 §4.4; bearer tokens, RFC 6750; JWT, RFC 7519,
  checked per RFC 8725). Every route requires an app-only access token for the Quelea API that carries the app role
  `Quelea.Client`; `/health` and the API documentation stay anonymous. The pattern (validators, guards, test token
  issuer) is taken over from Blackhole.
- **401 and 403 as Problem Details** (`unauthorized`, `forbidden`) with a `WWW-Authenticate: Bearer` challenge.
- **Event streams end when the token expires**, so a stream never outlives its credentials; clients reconnect with a
  fresh token and resynchronise.
- **Conditional requests (RFC 9110 §13).** Parties get a version. Reads return it as a strong `ETag`; `If-None-Match`
  answers 304. The leader commands `kick`, `promote`, `disband` and `warp` honour `If-Match` and answer 412
  `precondition-failed` when the party changed. Events carry the version.
- **Idempotent retries** (`Idempotency-Key`, draft-ietf-httpapi-idempotency-key-header). Every `POST` accepts the
  header; a repeated key replays the stored response instead of executing again; a key reused with a different request
  answers 422 `idempotency-key-reused`, a key whose first request is still running answers 409 `request-in-progress`
  with `Retry-After`.
- **Swagger UI** at `/swagger-ui` for the generated OpenAPI document.
- **Velocity plugin** fetches and caches tokens (`QUELEA_TENANT_ID`, `QUELEA_CLIENT_ID`, `QUELEA_CLIENT_SECRET`,
  `QUELEA_AUTH_SCOPE`), sends them on every request and on the stream, renews after a 401 once, and sends an
  `Idempotency-Key` with every command so that one retry after a timeout is safe.
- **`docs/entra-setup.md`** with the app registrations and a helper script.

## Capabilities

### New Capabilities
- `api-authentication`: who may call the service and how failures look.
- `conditional-requests`: party versions, `ETag`, `If-Match`, `If-None-Match`.
- `idempotent-requests`: `Idempotency-Key` semantics.

### Modified Capabilities
- `problem-details`: new codes.
- `velocity-plugin`: authentication and safe retries.

## Out of Scope

- Rate limiting: done in Envoy Gateway (local rate limit in the `BackendTrafficPolicy`), a Kubernetes-FLUX change.
- Binding a client to the players it may act for (a proxy acting only for its own players). Every client holding
  `Quelea.Client` is trusted equally.
- Human (delegated) tokens and per-route roles.

## Impact

- Backend: security configuration, problem handlers, party version in the store, a server filter for idempotency,
  event controller, OpenAPI views.
- Plugin: token provider, gateway, event stream, configuration.
- Operations: Entra app registrations (tenant admin), the backend's `QUELEA_AUTH_*` values in Kubernetes-FLUX, the
  plugin's client secret in the CloudNet proxy template. The new plugin must be rolled out **before** the backend
  enforces authentication; the 0.1.0 plugin cannot authenticate.
- `openspec/config.yaml`: principle 5 changes from "no auth layer" to Entra ID.
