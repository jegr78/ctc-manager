---
phase: 110-lobby-settings-graphic
plan: 05
status: complete
requirements: [LOBBY-01, LOBBY-02, LOBBY-03, LOBBY-04, LOBBY-05]
---

# Plan 110-05 Summary: Phase Verification

## Visual acceptance (Task 2 — blocking checkpoint)

**APPROVED** by operator (2026-05-31). Side-by-side review of the rendered Lobby Settings
template against `design-handoff/preview/lobby-settings-1920x1080.png`:
- Carbon vignette background, gold keyline `#f5c542`, Conthrax headers, 4 balanced columns,
  all 8 sections present, no clipping — matches the handoff design.
- Weather block shows the three D-07 rows (Selection Method / Preset / Custom).

**Captured artifacts** (`.screenshots/auto-uat/`, gitignored):
- `lobby-settings-editor-preview.png` — template-editor Lobby Settings tab live-preview
  (renders the real template via the same Thymeleaf path the service uses; no "Unknown
  template type").
- `lobby-settings-race-button.png` — race-detail "Generate Lobby Settings Graphic" button in
  the disabled-with-hint state ("Settings incomplete."), mirroring the Settings button.

**Demo-data limitations (not code defects), noted for operator UAT on real data:**
- Full 1920×1080 PNG generation could not be triggered in `dev,demo` — the demo season 2026
  has no cars/tracks assigned, so `race.getTrack() == null` → the gate correctly disables the
  button. The editor live-preview renders the identical template through the same code path.
- The match-detail "Post Lobby Settings" button only renders once a Discord channel is linked
  (no Discord credentials in demo); its markup mirrors the Post Settings block exactly.

## Authoritative gate (Task 3 — `./mvnw clean verify -Pe2e`)

**BUILD SUCCESS** (exit 0).

| Gate | Result |
|------|--------|
| Surefire (unit) | 1799 tests, 0 failures, 0 errors, 3 skipped |
| Failsafe (IT) | 534 tests, 0 failures, 0 errors, 2 skipped |
| E2E (Playwright) | 116 tests, 0 failures, 0 errors |
| **Total** | **~2449 tests green** (≥ 2416 baseline) |
| JaCoCo line coverage | **88.92%** — above the 82% gate ("All coverage checks have been met") |
| `LobbySettingsGraphicService` exclusion | confirmed in the JaCoCo `excludes` argLine (LOBBY-05) |
| SpotBugs + find-sec-bugs | **BugInstance size is 0** (DiscordPostService field documented in EI_EXPOSE_REP2 justification) |

No regressions; no new red/@Disabled tests.

## Requirement closure

- **LOBBY-01** — Lobby Settings graphic rendered purely from template variables, no Flyway
  migration, no new entity field. ✓
- **LOBBY-02** — per-race Generate button + endpoint + downloadable `lobby-settings.png`. ✓
- **LOBBY-03** — `DiscordPostType.LOBBY_SETTINGS` + manual `postLobbySettings(Match)` to the
  match channel. ✓
- **LOBBY-04** — template-editor Lobby Settings tab (load/save/reset/live-preview). ✓
- **LOBBY-05** — service JaCoCo-excluded; 82% line gate met. ✓

## Post-review re-verification (2026-05-31)

`/gsd-code-review 110` found 1 Blocker + 2 Warnings + 1 Info; all four fixed in-phase
(see `110-REVIEW.md` Resolution + commit `05eb3afd`). Authoritative gate re-run after the
fixes: **BUILD SUCCESS**, 1802 + 534 + 116 = **2452 tests** green, line coverage **88.95%**
(>82% gate), SpotBugs **BugInstance size 0**. +3 new tests (2 RaceServiceTest gate cases,
1 DiscordPostServicePreFlightTest track-guard case).

## Next steps

- **`/gsd-code-review 110`** — ✅ done; `110-REVIEW.md` present, all findings resolved.
- Operator UAT on real data: assign cars/tracks to a live season + link a Discord channel to
  exercise the full generate → download → Discord-post path end-to-end.

## Self-Check: PASSED
