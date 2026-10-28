# Design

## Context

All player-facing texts are MiniMessage templates in `lang/messages_en.properties` and `lang/messages_de.properties`,
rendered by `i18n/Messages`, which chooses German for a `de` client locale and English otherwise. Placeholders are
filled as unparsed text, so player names can never inject formatting.

## Decisions

### 1. Palette as MiniMessage tags in the bundles

Colours stay in the templates (no Java constants), so translations and operator overrides can restyle them. The
palette: brand gradient `#7c3aed → #db2777` (prefix, headers), commands `#c4b5fd`, arguments `#a78bfa`, text
`gray`, separators `dark_gray` (`»`, `•`), success `#86efac`, errors `#fca5a5`, hints `#fde68a`, leader `gold` with
`★`. A short colour guide at the top of the English bundle documents it.

### 2. Clickable help

Each help line uses `<click:suggest_command:'/party invite '>` and `<hover:show_text:'…'>`; the hover text is its own
key (`command.help.<name>.hover`), so it is translated with the rest. The help title gets a separator line.

### 3. Locale resolution and overrides

Bundles are looked up as `language_COUNTRY`, then `language`, then `en`. Built-in bundles come from the classpath;
files named `messages_<tag>.properties` in `plugins/quelea/lang/` (read once at start-up) are merged key by key over
the built-in bundle of the same tag or form a new language. A key missing in the chosen bundle falls back to the
English key, so a partial translation never shows a raw key.

Rejected: Adventure's `GlobalTranslator` with `TranslatableComponent` (it renders on the server per viewer, but
MiniMessage placeholders and click events in translations need a custom translator anyway, and the existing
per-locale rendering already does the job); hot reload of override files (a proxy restart is the normal way to apply
template changes).

## Risks / Trade-offs

- Operators can break a message with invalid MiniMessage in an override; the plugin logs the key and falls back to
  the built-in English text instead of sending nothing.
