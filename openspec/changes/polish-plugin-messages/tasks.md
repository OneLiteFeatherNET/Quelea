# Tasks

## 1. Translations

- [ ] 1.1 `Messages`: locale resolution `language_COUNTRY` → `language` → `en`, per-key fallback to English, override
      directory `plugins/quelea/lang/` merged over built-in bundles, invalid templates fall back with one log line.
- [ ] 1.2 Tests with a `@TempDir` override directory (new language, partial override, broken template).

## 2. Presentation

- [ ] 2.1 Palette applied to all keys in `messages_en.properties` and `messages_de.properties`, with a colour guide
      comment.
- [ ] 2.2 Clickable and hoverable help lines (`command.help.<name>.hover` keys).
- [ ] 2.3 Invite buttons with hover, member list with leader star, party chat format.
- [ ] 2.4 Bundle tests: every bundle has the English key set, every template parses, help lines carry click events.

## 3. Documentation

- [ ] 3.1 README: how to add a language or override a message in the proxy template.

## 4. Pull request

- [ ] 4.1 Open the code pull request `feat(velocity): give party messages colour, clickable help and open translations`.
