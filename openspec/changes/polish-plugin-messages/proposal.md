# Proposal

**Ships as:** `feat(velocity)`: one code pull request `feat(velocity): give party messages colour, clickable help and open translations`.

## Why

The first test on the team proxy showed that the party messages work but look flat: the help is a grey wall of
text, commands cannot be clicked, and success, failure and hints look alike. Players should see at a glance what
happened and be able to click a command instead of typing it.

Translations are fixed to English and German inside the jar. Adding a language or adjusting a colour needs a new
release, although the texts are plain MiniMessage templates that operators could adapt in the proxy template.

## What Changes

- **A consistent palette** across all messages: the existing violet-to-pink brand gradient for headers and the
  prefix, light violet for commands, grey for explanations, green for success, red for errors, amber for hints,
  gold with a star for the leader.
- **Clickable help:** every line of `/party` help suggests its command on click and explains it on hover; arguments
  are shown in a second colour.
- **Richer feedback:** invites with hover texts on [Accept] and [Deny], the member list with leader marker and
  online hint, the party chat with its own prefix and a coloured sender name.
- **Open translations:** the plugin picks the bundle for the client locale (`de_DE` → `de` → `en`) from any
  `messages_<language>.properties` it ships or finds in `plugins/quelea/lang/`; a file there overrides single keys of
  the built-in bundle, missing keys fall back to English. Operators can add a language or recolour a message
  without a release.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `velocity-plugin`: message presentation and translations.

## Out of Scope

- Redirecting normal chat into the party chat with a prefix (`@`) or a toggle. It needs SignedVelocity on the
  proxies and a Minestom implementation of its backend part, and comes as its own change.
- New languages beyond English and German in the jar.

## Impact

- Plugin only: message bundles, the `Messages` loader, command help and event handler rendering, tests.
