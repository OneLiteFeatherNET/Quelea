# Design

## Context

The backend logs through logback with JSON output and trace ids in the MDC (`export-metrics-via-otlp`, principle 4).
Errors reach the team today only as log lines and as error spans in Tempo. Every error response is an RFC 9457 Problem
Details answer from one global handler (principle 3), which is the single place where failures are classified.

The OneLiteFeather Sentry instance (sentry.onelitefeather.net) already receives events from Titan, Cygnus and Sturnus
through the Java SDK. The latest stable release of `io.sentry:sentry-bom` on Maven Central is **8.62.0**; all Sentry
dependency versions are taken from that BOM.

The velocity plugin runs inside the proxy JVM next to other plugins, so its dependencies must not leak into that
classpath (see D4).

## Decisions

### D1. Plain Sentry SDK, initialised by hand

Use `io.sentry:sentry` and `io.sentry:sentry-logback` with versions from `io.sentry:sentry-bom` 8.62.0. The backend
calls `Sentry.init` in a startup bean when the DSN is non-blank and `Sentry.close()` on shutdown. This is the same
pattern Titan and Cygnus use, so the team reads one way of doing it.

Rejected:
- A Micronaut Sentry integration. There is no official or maintained one, and a community module would add a framework
  dependency we would then have to keep current.
- `sentry-opentelemetry-agent`. It would duplicate tracing that already goes to Tempo and conflicts with principle 4,
  which keeps traces in OpenTelemetry.

### D2. Options and trace link

Options are set in one function that is unit-testable without network:
- `dsn` from `QUELEA_SENTRY_DSN`.
- `release` = `quelea@` + the application version.
- `environment` from `QUELEA_SENTRY_ENVIRONMENT`, default `production`.
- `serverName` = `HOSTNAME`.
- `sendDefaultPii` = `false`.
- No `tracesSampleRate`, so performance data stays off.
- Tag `component` = `backend`.

A `beforeSend` callback removes `request.headers`, `request.cookies` and the user IP as defence in depth. The current
OpenTelemetry trace id, which is already in the logging MDC, is attached as tag `trace_id`, so an event links to the
matching Tempo trace.

### D3. Backend reports through logback

A logback `SentryAppender` with `minimumEventLevel` ERROR and `minimumBreadcrumbLevel` INFO. Unhandled exceptions reach
the same path, because the global Problem Details handler logs every 5xx at ERROR.

Expected domain answers (404 party not found, 409 conflicts, 400 cannot-target-self, 401, 403, 412) are logged below
ERROR or not at all, so they never reach Sentry.

Rejected: capturing in the exception handler with `Sentry.captureException`. It would duplicate the log path, and the
two could disagree about what counts as an error.

### D4. Plugin: shaded SDK and an error-reporter port

The plugin shades and relocates Sentry (e.g. `net.onelitefeather.quelea.velocity.libs.sentry`), so it cannot clash with
other plugins on the proxy.

An `ErrorReporter` port has two implementations: a Sentry implementation and a no-op implementation used when the DSN is
blank. The reporting decisions live in the code that calls the port, so they are unit-testable with a fake.

Reported:
- Unexpected exceptions in command handlers, the event handler and warps.
- HTTP answers 5xx, or any status that is not a documented domain problem.
- 401 or 403 that remains after the token refresh retry, since the credentials are broken.
- The event stream staying disconnected for 5 minutes. One event per outage; a second one only after it reconnected.

Not reported:
- Single network errors or timeouts that the retry or reconnect recovers.
- Documented 4xx domain problems.

Tags: `component=velocity`, `proxy_id`, and `party_id` where known. Never player names or UUIDs.

Rejected: a Sentry call at each `catch` site. It would bind the decision to the Sentry SDK, so the rules could only be
tested against a live client.

### D5. Tests (F.I.R.S.T.)

- Backend: a unit test proves that no Sentry client is created for a blank DSN, and that PII is off, the release is set
  and the component tag is present for a non-blank DSN. The test uses the options-building function and needs no network.
- Plugin: reporting decisions are tested against a fake `ErrorReporter` with an injected `java.time.Clock`, so the
  5-minute outage rule is tested without sleeps and without a real Sentry.

## Risks

- **Sentry outage.** Sentry must never block or fail a request. The SDK sends asynchronously through a bounded queue,
  and a Sentry failure is logged, never rethrown.
- **Event volume during an outage.** Sentry rate limits apply, and the one-event-per-outage rule in D4 bounds the
  plugin's volume. The backend's volume is bounded by the number of ERROR logs, which the team watches in Sentry.
