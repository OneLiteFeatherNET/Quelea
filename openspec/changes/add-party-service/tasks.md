# Tasks

Every group lands its own JUnit tests (F.I.R.S.T.: in-memory fakes, injected `Clock`, no sleeps; Redis tests use
Testcontainers and are tagged `integration`).

## 1. Build and module skeleton

- [ ] 1.1 Inline version catalog in `settings.gradle.kts` (Micronaut platform catalog 5.0.2, Velocity 4.3.1, shadow,
      OpenAPI Generator, logstash encoder, OpenTelemetry logback MDC, janino), OneLiteFeather repositories, modules
      `backend`, `client`, `velocity`; root `subprojects` block with Java 25 release, JUnit platform, JaCoCo.
- [ ] 1.2 `backend` module: Micronaut application + AOT plugins, Netty runtime, main class
      `net.onelitefeather.quelea.backend.QueleaApplication`, `application.yml` with env defaults (design decision 10),
      `logback.xml` with the `LOG_JSON` switch, non-root user in `optimizedDockerfile`, Maven publication.
- [ ] 1.3 `docker/docker-compose.yml` with Dragonfly for local runs; switch `run-tests` on in `build-pr.yml`.

## 2. Problem Details

- [ ] 2.1 `Problem` record, `PartyProblem` enum (codes from design decision 6), `PartyException`.
- [ ] 2.2 Global handler(s) for `PartyException`, validation errors, unknown routes, 405 and unexpected errors,
      media type `application/problem+json`, `traceId` from the current span.
- [ ] 2.3 `docs/problems.md` with one section per code; a test asserts every enum code has a section.

## 3. Party domain

- [ ] 3.1 Ports `PartyStore`, `PresenceStore`, `LockService`, `EventPublisher`; in-memory implementations for tests.
- [ ] 3.2 `PartyService`: create, invite (by name, auto-create), list invites, accept, deny, leave, kick, promote,
      disband, warp, chat, with the rules of design decision 3; one event per success.
- [ ] 3.3 Unit tests for every scenario in `specs/party-management` and `specs/party-follow` (service side).

## 4. Dragonfly adapters

- [ ] 4.1 Redis implementations of the ports with the key layout of design decision 2 and the prefix setting.
- [ ] 4.2 Lock with `SET NX PX` and compare-and-delete release; single-party claim with `SET NX`.
- [ ] 4.3 Testcontainers tests (`integration`) including the concurrent-accept scenario.

## 5. Presence

- [ ] 5.1 `PresenceService`: login, disconnect (leave + drop invites), snapshot, heartbeat.
- [ ] 5.2 Dead-proxy reaper every 30 s under `lock:proxy-reaper`, using the injected `Clock`.
- [ ] 5.3 Tests for every scenario in `specs/network-presence`.

## 6. Events and SSE

- [ ] 6.1 Event envelope with `traceparent`; Redis pub/sub publisher on `quelea.events` and one subscriber per
      replica.
- [ ] 6.2 `GET /v1/events` SSE endpoint with keep-alive comments; fan-out span linked to the request span.
- [ ] 6.3 Tests for `specs/party-events` (fan-out with two in-process subscribers, keep-alive).

## 7. HTTP API

- [ ] 7.1 Controllers for the routes of design decision 4 with validation and OpenAPI annotations; OpenAPI file name
      fixed to `quelea.yml`.
- [ ] 7.2 Controller tests (Micronaut test with in-memory ports) for status codes and problem bodies.

## 8. Observability

- [ ] 8.1 OpenTelemetry HTTP tracing and OTLP exporter configured by `OTEL_*`; exclusions for `/health`,
      `/prometheus`.
- [ ] 8.2 Metrics of `specs/observability`, `/prometheus` endpoint, Redis health indicator on `/health`.

## 9. Client

- [ ] 9.1 `client` module generating the `java`/`native` client from the backend's `quelea.yml`, published as
      `net.onelitefeather:quelea-client`.

## 10. Velocity plugin

- [ ] 10.1 Plugin `quelea` (version from the build), config from `plugins/quelea/config.properties` with environment
      override, shadow jar `quelea-velocity-<version>.jar` with relocated dependencies.
- [ ] 10.2 Service gateway over the generated client on a virtual-thread executor; problem `code` mapping.
- [ ] 10.3 `/party` and `/pc` commands with tab completion of online names; English and German MiniMessage texts;
      clickable invite actions.
- [ ] 10.4 Listeners: login, disconnect, server connected (warp trigger with lobby pattern).
- [ ] 10.5 SSE consumer with backoff, snapshot after (re)connect, heartbeat every 15 s; event handlers that notify
      local players and perform the follow (design decision 7).
- [ ] 10.6 Tests with fakes for the gateway, the proxy and the clock (follow rules, reconnect backoff, command
      outcomes).

## 11. Documentation

- [ ] 11.1 README: what Quelea does, modules, local run, configuration tables for backend and plugin, operator steps
      (install the jar in the proxy template, set `QUELEA_URL`).

## 12. Pull request

- [ ] 12.1 Open the code pull request `feat(party): add distributed party service and velocity plugin`.
