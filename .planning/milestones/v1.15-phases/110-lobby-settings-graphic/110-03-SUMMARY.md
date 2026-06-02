---
phase: 110-lobby-settings-graphic
plan: 03
status: complete
requirements: [LOBBY-04]
requirements-completed: [LOBBY-04]
---

# Plan 110-03 Summary: Template-Editor Registration

## What was built

1. **`TemplateEditorController`** — all three registry maps (`TEMPLATE_TYPE_TO_BEAN`,
   `_ATTR`, `_LABEL`) converted `Map.of` → `Map.ofEntries` with `import static java.util.Map.entry`.
   Each gained the 11th `lobby-settings` entry (`lobbySettingsGraphicService` / `lobbySettings`
   / "Lobby settings"). The conversion is mandatory — `Map.of` caps at 10 pairs, so the 11th
   entry is a hard build-breaker. Existing 10 entries preserved verbatim; maps are private to
   the controller (verified no external callers via grep).

2. **`TemplatePreviewService`** — `renderPreview` switch gains `case "lobby-settings" ->
   buildLobbySettingsContext()` (default throw unchanged). The new builder sets font/logo,
   season vars, and a `v` map with the three D-07 weather keys (weatherMethod/weather/customWeather).
   **Per user feedback**, the match-specific sample values use neutral format placeholders —
   `roomName = "CTC – Season – Matchday – Home vs. Away"`, `track = "Track · Layout"`, `laps = "—"`
   — no concrete team names, real track, or fixed lap count.

3. **`template-editors.html`** — Lobby Settings tab button (after Power Rankings) + tab-content
   block (after the power-rankings block) with load/save/reset forms and the live-preview iframe.
   The variable help table reflects the actual context keys: added `v.weatherMethod` /
   `v.customWeather` (D-07), dropped `v.mechDamage` / `v.category` (D-05 — fixed in template,
   not match-specific). admin.css classes only, no inline styles.

4. **`TemplatePreviewServiceTest`** — `lobby-settings` added to the parameterized renderPreview
   smoke (now 9 types) + dedicated `givenLobbySettingsTemplate_whenRenderPreview_thenContainsWeatherVars`.
   Untagged unit test, green in the same commit.

5. **`TemplateEditorControllerTest`** — `givenLobbySettingsTabParam_whenGetTemplateEditors_thenReturnsLobbySettingsTab`
   asserts `activeTab=lobby-settings` + `lobbySettingsTemplate` / `lobbySettingsIsCustom`.

## User-directed deviation

- **Preview sample placeholders (D-04-adjacent):** user interrupted mid-execution to require neutral
  placeholders in `buildLobbySettingsContext` (no concrete team/track/lap data). Confirmed style via
  AskUserQuestion ("Format-Platzhalter"). Applied; the Task 2 commit was amended (unpushed) to carry
  the corrected values.

## Verification

- `./mvnw clean test-compile` — succeeds (no Map.of 11-entry break).
- `-Dtest=TemplatePreviewServiceTest` — 39 tests green (incl. 9-type smoke + weather-vars test).
- `-Dtest=TemplateEditorControllerTest` — 28 tests green (incl. new lobby-settings tab test).
- Editor live-preview visual check deferred to plan 110-05 (`/gsd-auto-uat 110` / playwright-cli).

## Commits

- `c5f27a9d` feat(110): register lobby-settings in template-editor registry maps
- `d43509c5` feat(110): add lobby-settings live-preview context (neutral placeholders)
- `af881dfb` feat(110): add Lobby Settings tab to the template editor
- `d96b614e` test(110): cover lobby-settings preview path
- `173fce00` test(110): assert lobby-settings editor tab + model attributes

## Self-Check: PASSED
