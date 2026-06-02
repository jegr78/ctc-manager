---
phase: 115-guest-marking-visibility
plan: 06
subsystem: ui
tags: [verification, visual-gate, playwright-cli, graphics, provisional, guest-marker]

requires:
  - phase: 115-02
    provides: "graphic guest markers (Scorecard/Provisional/Lineup)"
  - phase: 115-03
    provides: "admin race-/matchday-detail markers"
  - phase: 115-04
    provides: "ranking markers (admin + public)"
  - phase: 115-05
    provides: "public driver-profile marker + sub-label"
provides:
  - "Full e2e gate green on the final guest-marking code"
  - "User-approved guest marker treatment across all 6 MARK surfaces (SC-1)"
  - "Cross-graphic consistency confirmed (SC-2)"
  - "Standalone provisional-scores graphic generation (no Discord) — closes a Race-Results/Provisional inconsistency"
affects: [milestone-close]

tech-stack:
  added: []
  patterns:
    - "Guest star placed AFTER the driver name, uniformly across all surfaces (user decision at the visual gate)"
    - "Graphic marker nested inside .driver-name so it stays on the name line in the flex-column layout"

key-files:
  created:
    - .screenshots/115-results-render.png (+ -zoom)
    - .screenshots/115-provisional-scores.png (+ -zoom)
    - .screenshots/115-lineup-render.png (+ -zoom)
    - .screenshots/115-race-detail.png
    - .screenshots/115-matchday-detail.png
    - .screenshots/115-driver-ranking-admin.png
    - .screenshots/115-driver-ranking.png
    - .screenshots/115-driver-profile.png
  modified:
    - src/main/java/org/ctc/admin/service/ProvisionalScoresGraphicService.java
    - src/main/java/org/ctc/admin/service/RaceGraphicService.java
    - src/main/java/org/ctc/admin/controller/RaceController.java
    - src/main/java/org/ctc/domain/service/RaceService.java
    - src/main/resources/templates/admin/{results-render,provisional-scores-render,lineup-render,race-detail,matchday-detail,standings}.html
    - src/main/resources/templates/site/{driver-ranking,alltime-driver-ranking,driver-profile}.html
    - src/main/resources/static/admin/css/admin.css
    - src/main/resources/static/site/css/style.css
    - src/test/java/org/ctc/admin/service/RaceGraphicServiceTest.java
    - src/test/java/org/ctc/admin/controller/RaceControllerCalendarTest.java
    - src/test/resources/sitegen/baseline/single-league-driver-profile.html

key-decisions:
  - "Guest star placement: AFTER the driver name, uniform across all 6 surfaces (user picked 'after' over 'before' at the visual gate)"
  - "Graphic marker nested inside the .driver-name span (not a flex-column sibling) so it renders on the same line as the PSN id — fixes the earlier over/under stacking"
  - "Standalone provisional generation added (/admin/races/{id}/generate-provisional) — provisional now behaves like Race Results; Discord is no longer required to produce the graphic"

patterns-established:
  - "Visual gate may refine the locked treatment (placement) — captured here as after-name + provisional standalone"

requirements-completed: [MARK-01, MARK-02, MARK-03, MARK-04, MARK-05, MARK-06]

duration: 3h
completed: 2026-06-02
---

# Phase 115 Plan 06: Final Gate + Visual Approval Summary

**All six guest-marking surfaces verified visually with real guest data and approved by the user; the guest star sits after the driver name uniformly everywhere, and the provisional-scores graphic is now generatable standalone (no Discord), closing a Race-Results/Provisional inconsistency.**

## Performance

- **Tasks:** 3 (e2e gate, screenshots, user approval) + 2 visual-gate refinements
- **Surfaces captured:** 8 screenshots under `.screenshots/115-*.png`

## Accomplishments

- **Task 1 — e2e gate:** `./mvnw clean verify -Pe2e` green on the final code (see Verification).
- **Task 2 — screenshots:** captured all marked surfaces with real guest data (`Test_Guest_1` pure guest + `Test_DualRole_1` dual-role). Public-site surfaces required temporarily renaming the filtered "Test-Season 2026" (the public generator excludes `name.contains("Test")`) and regenerating via the in-app "Generate Site" action — ephemeral (dev re-seeds on restart).
- **Task 3 — user approval (SC-1/SC-2):** user reviewed and approved the treatment.

## Visual-gate refinements (in scope per the plan's placement-adjustment clause)

1. **Marker placement fixed + finalized.** Initially the graphic marker stacked above/below the name (flex-column sibling) and was inconsistent (Scorecard above, Lineup below). Fixed by nesting the marker inside the `.driver-name` line, then — per the user's decision — placed it AFTER the name uniformly across all 6 surfaces (admin.css/style.css `margin-right`→`margin-left`; the graphic inline rules likewise). Driver-profile byte-identity baseline refreshed (whitespace-only reorder for the non-guest fixture; verified by diff).
2. **Standalone provisional generation (new).** The user identified that Race Results has a standalone generate (`/generate-results`) while Provisional only had the Discord-post path — an inconsistency, since the two graphics behave identically. Added `ProvisionalScoresGraphicService.generateProvisionalFile(Race)` (saves `provisional.png`, computes raceIndex internally), wired `RaceGraphicService.generateProvisional(UUID)` + `POST /admin/races/{id}/generate-provisional` + `RaceDetailData.canGenerateProvisional/provisionalExists` + a race-detail button. `RaceGraphicServiceTest` extended; `RaceControllerCalendarTest` construction updated for the new record fields.

## Verification

- `./mvnw clean verify -Pe2e`: **BUILD SUCCESS** — 1432 Surefire + 641 Failsafe (incl. Playwright E2E), 0 failures/errors; JaCoCo coverage checks met; SpotBugs 0; Checkstyle clean.
- Targeted before the full run: `RaceGraphicServiceTest` (8, incl. new provisional test), `RaceServiceTest` (18), `RaceControllerCalendarTest` (9), `DriverProfilePageGeneratorIT` (8) all green.
- SC-1: user approved (after-name treatment). SC-2: Scorecard + Provisional + Lineup all mark guests via the same mechanism; the other 9 `*-render` templates are team-only (audited).

## Self-Check: PASSED

All six MARK requirements visually confirmed with real data; treatment user-approved; provisional standalone gap closed.
