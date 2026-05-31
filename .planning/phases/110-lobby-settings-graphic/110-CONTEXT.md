# Phase 110: Lobby Settings Graphic - Context

**Gathered:** 2026-05-31
**Status:** Ready for planning

<domain>
## Phase Boundary

Add a new **Lobby Settings** graphic: a 1920×1080 Carbon/Gold PNG rendering the full GT7
lobby-settings overview (9 sections, ~60 values) in 4 balanced columns. It mirrors the existing
`SettingsGraphicService` pipeline (Thymeleaf template → Playwright screenshot) 1:1. Delivered as a
high-fidelity drop-in handoff (template + service + editor snippet) staged under
`design-handoff/`. The graphic is driven purely by template variables — **no new data model, no
Flyway migration** (LOBBY-01). Wiring covers: new `LobbySettingsGraphicService` (implements
`TemplateManageable`), per-race generate button on `race-detail.html`, `RaceGraphicService` +
`RaceController` endpoints, template-editor tab (3 `Map.of`→`Map.ofEntries` + `TemplatePreviewService`
case), a new Discord post type, and JaCoCo coverage exclusion.

**In scope:** LOBBY-01..05 (service+template, preview/download, Discord post type, template-editor
override, coverage exclusion).
**Out of scope:** any RaceSettings schema change; restyling the existing 12 graphics (separate
handoff, not this phase); custom-weather as a dedicated DB field.
</domain>

<decisions>
## Implementation Decisions

### Discord Posting (LOBBY-03)
- **D-01:** Mirror the existing `SETTINGS` post exactly — add `DiscordPostType.LOBBY_SETTINGS`,
  post via a **manual** admin button (`postLobbySettings`, family of `postSettings` → `postRaceBundle`),
  targeting the **match channel** (`discord_channel_id` on `Match`). NOT an event-triggered auto-post,
  NOT announcement/forum. Rationale: lobby settings are match-specific config; same destination and
  trigger semantics as the Settings graphic keep the operator model consistent.
- Note: like `postSettings`, the Discord post operates at **Match** level (bundle), while the PNG
  *generation* is per-Race on `race-detail.html` — same split as the existing Settings flow.

### Generate Gating & UX (LOBBY-02)
- **D-02:** Per-race **Generate Lobby Settings Graphic** button on `race-detail.html`, mirroring the
  Settings button's **disabled-with-hint** pattern (not "always active + error flash"). UI consistency
  with the adjacent Settings button.
- **D-03:** Gate via a new `canGenerateLobbySettings` flag in `RaceService` =
  `hasAllSettings && race.getTrack() != null && !lobbySettingsGraphicExists`. **Crucially does NOT
  require team cards** (unlike `canGenerateSettings`, which requires home+away cards). Disabled-hint
  text reflects only missing settings / missing track.
- Service guard mirrors the handoff: throw `IllegalStateException` when track is null or
  `!race.hasAllSettings()` (`Race.hasAllSettings()` exists at `Race.java:123`).

### fest ↔ match-spezifisch Split (LOBBY-01)
- **D-04:** Base split = handoff proposal. Match-specific (`${v.*}`): Room Name, Track, No. of Laps,
  Preset/Custom Weather (see D-06), Time of the Day, Variable Time Speed Rate, Tyre Wear Rate, Fuel
  Consumption Rate, Refuelling Speed, Initial Fuel, Minimum No. of Pit Stops, Usable Tyre & Types,
  Required Tyre Type. Everything else = fixed league default in the template (editor-adjustable per
  season via the `${v.key} ?: 'default'` natural-template convention).
- **D-05:** **Filter by Category** stays a fixed `"—"` (no car-category field in the model; confirmed
  no `category` getter on `RaceSettings`). **Mechanical Damage** stays a fixed template default
  ("Light" per handoff). No backend mapping for either.

### Weather: Preset vs Custom (LOBBY-01, no schema change)
- **D-06:** Distinguish Preset vs Custom **per race from the single existing `weather` String field**
  (`RaceSettings.weather`, free text; existing data shape e.g. `"Preset S02"`). Service parses the
  prefix and populates three derived template vars:
  - Value starts with **"Preset"** → strip prefix → remainder (e.g. `S01`, `C04`) into
    **Preset Weather** (`v.weather`); **Custom Weather** (`v.customWeather`) = `"—"`;
    **Weather Selection Method** (`v.weatherMethod`) = `"Preset Weather"`.
  - Value starts with **"Custom"** → **Preset Weather** = `"—"`; the individual slot sequence
    (e.g. `R01, ?, R04, C05, S11, ?, C01, R08`) into **Custom Weather**;
    **Weather Selection Method** = `"Custom Weather"`.
  - Fallback (no recognised prefix) → treat as Preset, raw value into Preset Weather.
- **D-07 (template tweak required):** The handoff template currently **hardcodes** the
  "Weather Selection Method" row (`Preset Weather`) and defaults the Preset Weather row to `'S01'`.
  Wire all three rows to the derived vars with `"—"` fallbacks:
  `Weather Selection Method` → `${v.weatherMethod} ?: 'Preset Weather'`,
  `Preset Weather` → `${v.weather} ?: '—'`, `Custom Weather` → `${v.customWeather} ?: '—'`.
  This is the only intentional deviation from the pure drop-in template.

### Room Name Format
- **D-08:** `"CTC – {year} – {matchdayLabel} – {homeShort} vs. {awayShort}"` (handoff default,
  em-dashes, team `getShortName()`). Falls back to `"CTC – {year} – {matchdayLabel}"` when home/away
  are absent. Implemented in the service's `buildRoomName(...)` helper.

### Coverage (LOBBY-05)
- **D-09:** Add `org/ctc/admin/service/LobbySettingsGraphicService.class` to the JaCoCo
  `<exclude>` list in `pom.xml` (Playwright-runtime services block, alongside the other
  GraphicService exclusions at pom.xml ~L374-388). 82% line gate must still pass.

### Claude's Discretion
- Exact service method bodies (`renderTemplate`/`loadTemplate`/`loadDefaultTemplate`/`saveTemplate`/
  `resetTemplate`/`hasCustomTemplate`) are a 1:1 mirror of `SettingsGraphicService` — handled by planner.
- Whether to add a `TemplatePreviewService.buildLobbySettingsContext()` exactly as in the handoff
  README (it is required so the editor live-preview does not throw "Unknown template type").
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase handoff (drop-in source of truth — read first)
- `.planning/phases/110-lobby-settings-graphic/design-handoff/README.md` — step-by-step integration
  (9 steps), file→target mapping, design tokens, the "fest ↔ match-spezifisch" principle.
- `.planning/phases/110-lobby-settings-graphic/design-handoff/codebase-snippets/INTEGRATION.md` —
  condensed service shape + wiring; `Map.of`→`Map.ofEntries` build-breaker warning.
- `.planning/phases/110-lobby-settings-graphic/design-handoff/java/LobbySettingsGraphicService.java` —
  ready-to-place service (drop into `src/main/java/org/ctc/admin/service/`).
- `.planning/phases/110-lobby-settings-graphic/design-handoff/templates/lobby-settings-render.html` —
  ready-to-place template (drop into `src/main/resources/templates/admin/`); **apply D-07 weather tweak**.
- `.planning/phases/110-lobby-settings-graphic/design-handoff/snippets/template-editor-tab.html` —
  editor tab-button + tab-content snippet for `template-editors.html`.
- `.planning/phases/110-lobby-settings-graphic/design-handoff/preview/lobby-settings-1920x1080.png` —
  target visual (acceptance reference).

### Requirements & roadmap
- `.planning/REQUIREMENTS.md` §LOBBY (LOBBY-01..05).
- `.planning/ROADMAP.md` §"Phase 110: Lobby Settings Graphic" (5 success criteria).

### Mirror-pattern source files (in-repo)
- `src/main/java/org/ctc/admin/service/SettingsGraphicService.java` — 1:1 structural mirror target
  (constructor, generateSettings, TemplateManageable methods, DEFAULT/CUSTOM constants).
- `src/main/java/org/ctc/admin/service/AbstractGraphicService.java` — `FONT_CLASSPATH`,
  `CTC_LOGO_CLASSPATH`, `renderScreenshot`, `encodeClasspathResource`, `processStringTemplate`.
- `src/main/resources/templates/admin/settings-render.html` — existing weather row reference (L110-111).

### No external ADRs/specs beyond the above — requirements fully captured in decisions.
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `SettingsGraphicService` — exact structural template for the new service (same constructor shape
  `(TemplateEngine, @Value uploadDir)` minus the StandingsService/PlayoffSeedRepository deps the
  Settings service has; Lobby needs none of those).
- `AbstractGraphicService` — provides `FONT_CLASSPATH = static/admin/fonts/ConthraxSb.woff2`,
  `CTC_LOGO_CLASSPATH = static/admin/img/ctc-logo-white.png`, `renderScreenshot(html, out)` (1920×1080,
  fullPage:false), `encodeClasspathResource`, `processStringTemplate`.
- `RaceSettings` getters — all 11 needed getters exist (numberOfLaps, weather, timeOfDay,
  timeProgressionMultiplier, tyreWearMultiplier, fuelConsumptionMultiplier, refuelingSpeed,
  initialFuel, numberOfRequiredPitStops, availableTyres, mandatoryTyres). NO customWeather / category
  field (→ D-05, D-06).

### Established Patterns
- Per-race generate flow: `RaceController.generateSettings` (L318-327) → `RaceGraphicService.generateSettings`
  (L38-40) → `generateAndSaveGraphic(raceId, "Settings", svc::generateSettings)` → saved as `RaceAttachment`.
- Gating flag pattern: `RaceService` builds `canGenerateSettings` (L143). Mirror as `canGenerateLobbySettings`
  per D-03 (drop the card requirement).
- Disabled-with-hint button block: `race-detail.html` L52-61.
- Discord: `DiscordPostType` enum + `DiscordPostService` channel routing; SETTINGS → match channel,
  manual `postSettings(Match)` → `postRaceBundle` (DiscordPostService ~L730). Mirror as LOBBY_SETTINGS.
- Template editor registry: `TemplateEditorController` 3× `Map.of(...)` each with **exactly 10** entries
  (L22-59) → convert all three to `Map.ofEntries(...)` + add the 11th `lobby-settings` entry (build-breaker
  if left as Map.of). `TemplatePreviewService.renderPreview` switch (L45-60) needs a `case "lobby-settings"`.

### Integration Points
- New files: `LobbySettingsGraphicService.java`, `templates/admin/lobby-settings-render.html`.
- Edited: `RaceGraphicService`, `RaceController`, `RaceService` (gate), `race-detail.html` (button),
  `TemplateEditorController` (3 maps), `TemplatePreviewService` (case + builder), `template-editors.html`
  (tab snippet), `DiscordPostType` + `DiscordPostService` (+ controller button for post), `pom.xml` (JaCoCo).
- **Shared-file clobber risk:** `RaceController`, `race-detail.html`, `template-editors.html`,
  `TemplateEditorController`, `DiscordPostService` are large shared files — APPEND, do not rewrite.
</code_context>

<specifics>
## Specific Ideas

- Weather encoding is the one place the operator's data-entry convention matters: admins signal mode by
  typing `"Preset <code>"` or `"Custom <slot, slot, …>"` into the existing weather field (D-06). `?`
  placeholders inside a custom sequence are valid free text.
- Visual target is fixed by `design-handoff/preview/lobby-settings-1920x1080.png` — Carbon vignette,
  gold keyline `#f5c542`, Conthrax headers, 4-column balanced ledger.
</specifics>

<deferred>
## Deferred Ideas

- Restyling/Carbon-polish of the other 12 render templates (separate "CTC Team Cards Redesign" handoff)
  is NOT this phase — its own future phase/milestone.
- A dedicated `customWeather` + `weatherMode` schema field (Flyway) — explicitly rejected for this phase
  (LOBBY-01 forbids migration; D-06 prefix convention used instead). Revisit only if the prefix
  convention proves fragile in operation.

None other — discussion stayed within phase scope.
</deferred>

---

*Phase: 110-lobby-settings-graphic*
*Context gathered: 2026-05-31*
