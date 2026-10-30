# Proposal

**Ships as:** `feat(observability)`: one code pull request `feat(observability): report errors to sentry`.

## Why

Before Quelea goes onto the live proxies (Proxy-1, Proxy-2), the team wants to see failures without reading proxy logs on
mc01 or pod logs. Today an error is only visible in logs and as error spans in Tempo; nobody is notified and nothing is
grouped. On 2026-10-28 the team proxy failed TLS for an hour, and only a manual Tempo search found it.

OneLiteFeather already runs a self-hosted Sentry (sentry.onelitefeather.net) used by Titan, Cygnus and Sturnus, and a
`quelea` project exists there. Reusing it gives Quelea grouped, notified errors without a new service to operate.

## What Changes

- The backend reports ERROR log events and unhandled exceptions to Sentry.
- The velocity plugin reports unexpected failures to Sentry.
- Both tag events with `component=backend|velocity`, `release=quelea@<version>` and `environment`.
- The backend reads the DSN from `QUELEA_SENTRY_DSN`. The plugin reads the config key `sentry-dsn` or the environment
  variable `QUELEA_SENTRY_DSN`.
- The environment comes from `QUELEA_SENTRY_ENVIRONMENT` (backend) or `sentry-environment` (plugin), defaulting to
  `production`.
- A blank DSN switches reporting off.
- Principle 4 of `openspec/config.yaml` is amended to allow error reporting to Sentry, and only errors.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `observability`: error reporting to Sentry.
- `velocity-plugin`: error reporting of plugin failures.

## Out of Scope

- Sentry performance tracing and profiling. Traces stay in Tempo via OpenTelemetry.
- Personal data. No IP addresses, no player names or UUIDs, no request headers in events.
- Alerting rules in Sentry. They are configured in the Sentry UI in a later step.
- Kubernetes-FLUX secret wiring. It ships in a separate FLUX pull request.

## Impact

- Backend: the Sentry dependency through the BOM, one startup bean, a logback appender configuration, tests.
- Velocity: Sentry shaded and relocated into the plugin jar, an error-reporter port with a Sentry and a no-op
  implementation, config keys, tests.
- Kubernetes-FLUX: SOPS secret `quelea-sentry` injected via `envFrom`.
- CloudNet: the template `Global/Global-Proxy-Quelea` gets the DSN in its `config.properties`.
