---
phase: 110-lobby-settings-graphic
plan: 02
status: complete
requirements: [LOBBY-02]
---

# Plan 110-02 Summary: Per-race Generate/Download Flow

## What was built

1. **`RaceService`** — computes `lobbySettingsGraphicExists` (FILE attachment whose URL
   ends `/lobby-settings.png`) and extends `RaceDetailData` with three appended fields:
   `canGenerateLobbySettings`, `lobbySettingsMissing`, `lobbySettingsExist`. Gate =
   `hasAllSettings && !lobbySettingsGraphicExists` — **no team-card requirement** (D-03,
   the deliberate difference from `canGenerateSettings`). Inlined into the existing
   `new RaceDetailData(...)` call to match the surrounding style.

2. **`RaceGraphicService`** — new `final LobbySettingsGraphicService` dependency
   (Lombok constructor) + `@Transactional generateLobbySettings(UUID)` delegating to
   `generateAndSaveGraphic(raceId, "LobbySettings", lobbySettingsGraphicService::generateLobbySettings)`.

3. **`RaceController`** — `POST /{id}/generate-lobby-settings` (try/catch RuntimeException,
   success message "Lobby settings graphic generated", redirect to `/admin/races/{id}`),
   plus the three lobby model attributes in `detail()`. Existing endpoints untouched.

4. **`race-detail.html`** — Lobby Settings button block inserted after the Settings block:
   active form (`btn btn-primary`) + disabled-with-hint span (`btn-secondary`,
   `text-dim` hint showing only "Settings incomplete." per D-03 — no team-card hint).
   admin.css classes only, no inline styles.

## Deviation / cross-file impact

- **Test callsite fix (in scope):** `RaceControllerCalendarTest.detailData(...)` constructs
  `RaceDetailData` directly and broke on the new record arity. Added `false, false, false`
  for the three lobby fields (irrelevant to calendar tests). Caught by `grep -rn "new RaceDetailData("`
  per the "Grep All Usages Before Refactor" rule — only two callsites exist (service + this test).

## Decisions honored

- D-02: disabled-with-hint pattern (not always-active + error-flash).
- D-03: gate omits team-card requirement; hint omits team-card message.

## Verification

- `./mvnw clean test-compile` — succeeds (record arity matches both callsites).
- Endpoint/symbol audit: `generateLobbySettings`, `canGenerateLobbySettings`,
  `generate-lobby-settings` all created here; `generateAndSaveGraphic` + the
  `lobbySettingsGraphicService` bean exist from prior code + plan 110-01.
- Full `clean verify -Pe2e` is the phase-end gate (deferred to phase close / plan 110-05).
- Visual fidelity of the rendered PNG: operator checkpoint in plan 110-05.

## Commits

- `52a2d6a0` feat(110): add canGenerateLobbySettings gate to RaceService
- `f0626afd` feat(110): wire lobby-settings generate endpoint + model attrs
- `a4313cab` feat(110): add Lobby Settings generate button to race-detail

## Notes for downstream plans

- The attachment suffix is `"LobbySettings"`; attachment name format follows the shared
  `generateAndSaveGraphic` (`{matchdayLabel}-{home}-{away}-LobbySettings`).
- PNG download path: `/uploads/races/{id}/lobby-settings.png`.

## Self-Check: PASSED
