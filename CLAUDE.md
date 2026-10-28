# Quelea

Party system for the OneLiteFeather network: a Micronaut service (`backend`), a Velocity plugin (`velocity`) and a generated Java client (`client`). Only the repository skeleton exists so far; the modules arrive in later pull requests.

## Commands

- `./gradlew build` - full build
- `./gradlew :backend:test -PexcludeTags=integration` - unit tests without Testcontainers

## Conventions

- Conventional Commits for commits and PR titles (`type(scope): imperative summary`); release-please reads them.
- Squash merge only; the PR title becomes the commit on main.
- OpenSpec: the proposal goes in its own PR (`docs(openspec): propose <change>`) and is merged before the code PR. Specs live in `openspec/`.
- Tests follow F.I.R.S.T.: no sleeps, injected `java.time.Clock`, no shared static state, Testcontainers tests tagged `integration`.
- No Claude attribution (no co-author trailers, no "Generated with" lines) in commits or PRs.
