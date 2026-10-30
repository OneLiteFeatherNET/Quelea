# Tasks

## 1. Backend

- [ ] 1.1 Add `io.sentry:sentry` and `io.sentry:sentry-logback` through `io.sentry:sentry-bom` 8.62.0.
- [ ] 1.2 Startup bean that calls `Sentry.init` when `QUELEA_SENTRY_DSN` is non-blank and `Sentry.close()` on shutdown.
- [ ] 1.3 Logback `SentryAppender` with minimum event level ERROR and minimum breadcrumb level INFO.
- [ ] 1.4 `beforeSend` callback that drops request headers, cookies and the user IP.
- [ ] 1.5 Attach the current trace id from the MDC as tag `trace_id`.
- [ ] 1.6 Tests: no client for a blank DSN; PII off, release, environment and `component=backend` set for a non-blank DSN; scrub callback removes headers and IP.

## 2. Velocity

- [ ] 2.1 Shade and relocate Sentry into the plugin jar.
- [ ] 2.2 `ErrorReporter` port with a Sentry implementation and a no-op implementation.
- [ ] 2.3 Config key `sentry-dsn` and `sentry-environment`, with the environment variables `QUELEA_SENTRY_DSN` and `QUELEA_SENTRY_ENVIRONMENT`.
- [ ] 2.4 Report points: command handlers, event handler, warps, 5xx and unexpected statuses, 401/403 after the token refresh retry.
- [ ] 2.5 Event-stream outage rule: one report per outage after 5 minutes, using an injected `java.time.Clock`.
- [ ] 2.6 Tests against a fake `ErrorReporter`: reported and not-reported cases, the outage rule, no player data in tags.

## 3. Documentation

- [ ] 3.1 README: the Sentry keys and environment variables, and that reporting is off without a DSN.
- [ ] 3.2 Example config for the velocity plugin with the new keys.

## 4. Pull request

- [ ] 4.1 Open the code pull request `feat(observability): report errors to sentry`.
