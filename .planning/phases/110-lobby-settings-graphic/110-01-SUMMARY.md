---
phase: 110-lobby-settings-graphic
plan: 01
status: complete
requirements: [LOBBY-01, LOBBY-05]
---

# Plan 110-01 Summary: LobbySettingsGraphicService Foundation

## What was built

The foundation every downstream plan consumes:

1. **`templates/admin/lobby-settings-render.html`** — drop-in 1920×1080 Carbon-HUD
   ledger (4 balanced columns, gold `#f5c542` keyline, Conthrax headers). Row markup
   is verbatim from the design handoff; the **D-07 weather tweak** is applied — the
   three weather rows now bind `${v.weatherMethod}`, `${v.weather}`, `${v.customWeather}`
   with `?: 'Preset Weather'` / `?: '—'` fallbacks (no hardcoded `S01`/`Preset Weather`
   literal). All comment pollution stripped to match the `settings-render.html` house
   style (0 comments).

2. **`LobbySettingsGraphicService.java`** — 1:1 structural mirror of
   `SettingsGraphicService`: 2-arg constructor `(TemplateEngine, @Value uploadDir)`,
   `implements TemplateManageable`, no Standings/PlayoffSeed deps.
   `generateLobbySettings(Race)` guards null track / incomplete settings with
   `IllegalStateException` (D-03, **no team-card check**), builds the 16-key `v` map,
   renders to `uploadDir/races/{id}/lobby-settings.png`, returns the
   `/uploads/races/{id}/lobby-settings.png` URL. Weather parse (D-06) and
   `buildRoomName` (D-08) are **package-private static helpers** → unit-testable without
   Playwright. `WeatherDisplay` record carries the three derived weather strings.

3. **`LobbySettingsWeatherParsingTest.java`** — 6 Given-When-Then cases (Preset-prefixed,
   Custom-prefixed, unprefixed→Preset, blank→dashes, both-teams room name, missing-team
   room name). Plain untagged unit test, committed green with the implementation.

4. **`pom.xml`** — appended `org/ctc/admin/service/LobbySettingsGraphicService.class` to
   the JaCoCo exclude block (D-09 / LOBBY-05). One line added, block otherwise unchanged.

## Decisions honored

- D-04/D-05: fest↔match split per handoff; Filter-by-Category fixed `—`, Mechanical
  Damage fixed `Light` (no backend mapping, no `category` getter).
- D-06: weather mode derived from the single existing `weather` String by prefix.
- LOBBY-01: **no Flyway migration, no new entity field** — fully template-driven.

## Verification

- `./mvnw clean test-compile` — succeeds.
- `LobbySettingsWeatherParsingTest` — Tests run: 6, Failures: 0, Errors: 0.
- pom.xml JaCoCo exclude present.

## Commits

- `259f59b5` feat(110): add lobby-settings Carbon-HUD render template
- `c48e165e` feat(110): add LobbySettingsGraphicService
- `2f161f82` test(110): unit-test lobby weather parsing + room name; JaCoCo-exclude service

## Notes for downstream plans

- Bean name resolves to `lobbySettingsGraphicService`; registers in
  `TemplateEditorController`'s `Map<String, TemplateManageable>` via the interface (plan 110-03).
- `generateLobbySettings(Race)` is the entry point plan 110-02 wires into `RaceGraphicService`.
- Custom-template file constant is `lobby-settings-template.html` (under uploadDir).

## Self-Check: PASSED
