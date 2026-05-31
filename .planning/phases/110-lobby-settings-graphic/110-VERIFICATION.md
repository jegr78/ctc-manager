---
phase: 110
type: verification
verdict: PASS
verified: 2026-05-31
note: backfilled retroactively during /gsd-audit-milestone v1.15 follow-up
---

# Phase 110 Verification — Lobby Settings Graphic

Goal-backward verification: does the codebase deliver the phase goal — admins can
generate, preview, download, and Discord-post a new Carbon-HUD "Lobby Settings" graphic
driven entirely by template variables (no new data model, no Flyway), integrated into the
existing graphic service, template editor, and Discord post infrastructure?

## Requirement coverage

| Req | Criterion | Verdict | Evidence |
|-----|-----------|---------|----------|
| LOBBY-01 | New `LobbySettingsGraphicService` renders Carbon-HUD from template variables; no data model / Flyway | ✅ PASS | `LobbySettingsGraphicService` mirrors `SettingsGraphicService` (2-arg ctor, `implements TemplateManageable`); `lobby-settings-render.html` 1920×1080 Carbon HUD; D-07 weather rows template-bound. No new entity field, no migration. `LobbySettingsWeatherParsingTest` ×6 green. Visual acceptance APPROVED by operator vs design handoff. |
| LOBBY-02 | Admin can preview and download the graphic | ✅ PASS | `RaceService` gate `canGenerateLobbySettings` (`hasAllSettings && !exists`, no team-card req per D-03); `RaceGraphicService.generateLobbySettings(UUID)`; `RaceController` `POST /{id}/generate-lobby-settings`; `race-detail.html` button (active + disabled-with-hint). PNG at `/uploads/races/{id}/lobby-settings.png`. |
| LOBBY-03 | Available as a new Discord post type into the existing integration | ✅ PASS | `DiscordPostType.LOBBY_SETTINGS`; `DiscordPostService.postLobbySettings(Match)` `@Transactional`, posts to MATCH channel via `postRaceBundle` (D-01 manual); `MatchController` `POST /{id}/post-lobby-settings`; `MatchService.buildDetailModel` `lobbySettingsPost`; `match-detail.html` Post/Re-Post/disabled states. 5 `DiscordPostService` test ctors updated. Integration check: wiring CONNECTED end-to-end. |
| LOBBY-04 | Template editable via the template editor (`implements TemplateManageable`) | ✅ PASS | `TemplateEditorController` registry maps `Map.of → Map.ofEntries` + 11th `lobby-settings` entry; `TemplatePreviewService` `case "lobby-settings"` (neutral placeholders per user feedback); `template-editors.html` tab + live-preview. `TemplatePreviewServiceTest` (39) + `TemplateEditorControllerTest` (28) green. |
| LOBBY-05 | Service JaCoCo-excluded (Playwright runtime); 82% line gate met | ✅ PASS | `org/ctc/admin/service/LobbySettingsGraphicService.class` in JaCoCo `excludes` argLine; coverage 88.95% > 82% gate. |

## Strategy fidelity (CONTEXT decisions)
- **Template-variable-driven, no data model / Flyway** (LOBBY-01 / D-09).
- **Gate omits team-card requirement** (D-03) — deliberate difference from `canGenerateSettings`.
- **Manual MATCH-channel post** (D-01) — not announcement/forum/auto-post.
- **External design handoff** consumed (delivered 2026-05-31), Phase-105 CARD-01 pattern.

## Evidence
Authoritative gate (110-05): `./mvnw clean verify -Pe2e` **BUILD SUCCESS** — after
110-REVIEW.md fixes: 1802 + 534 + 116 = **2452 tests** green, line coverage **88.95%**,
SpotBugs `BugInstance size 0`. 110-REVIEW.md resolved (1 Blocker + 2 Warnings + 1 Info,
all fixed; +3 tests). Operator UAT on real data (cars/tracks + linked Discord channel)
noted as a post-deploy operator action (demo data has no track → gate correctly disables).

## Verdict: PASS — all of LOBBY-01..05 satisfied.
