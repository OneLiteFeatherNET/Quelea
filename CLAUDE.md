# Quelea

Party system for the OneLiteFeather network: a Micronaut service (`backend`), a Velocity plugin (`velocity`) and a generated Java client (`client`).

## Modules

- `backend`: packages `api` (controllers, requests, metrics), `party` (rules and ports), `presence` (players per proxy, dead-proxy reaper), `event` (publishing, SSE fan-out), `problem` (RFC 9457 errors), `store.redis` (Dragonfly adapters).
- `client`: generated from the backend's `quelea.yml` at build time. Never edit generated code; change the backend's API instead.
- `velocity`: plain HTTP + Gson against the service. It has no dependency on `client`.

## Commands

- `./gradlew build` - full build
- `./gradlew :backend:test -PexcludeTags=integration` - unit tests without Testcontainers
- `./gradlew :velocity:runVelocity` - run a local Velocity proxy with the plugin
- `./gradlew :backend:run` - run the service (needs Dragonfly from `docker/docker-compose.yml`)

## Conventions

- Conventional Commits for commits and PR titles (`type(scope): imperative summary`); release-please reads them.
- Squash merge only; the PR title becomes the commit on main.
- OpenSpec: the proposal goes in its own PR (`docs(openspec): propose <change>`) and is merged before the code PR. Specs live in `openspec/`.
- Tests follow F.I.R.S.T.: no sleeps, injected `java.time.Clock`, no shared static state, Testcontainers tests tagged `integration`.
- No Claude attribution (no co-author trailers, no "Generated with" lines) in commits or PRs.
