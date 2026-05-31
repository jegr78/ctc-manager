---
phase: 109
plan: "04"
subsystem: standings-views
tags: [thymeleaf, css, standings, walkover, e2e]
requires: [109-02, 109-03]
provides:
  - .match-wo CSS class
  - "w/o" forfeiter label in admin matchday-detail
  - "(w/o)" note in public standings (driven by TeamStanding.hasWalkover)
  - WalkoverE2ETest (enabled, green)
affects: [matchday-detail.html, site/standings.html, admin.css, WalkoverE2ETest]
tech-stack:
  added: []
  patterns: [th:if-presence-check, byte-identical-sitegen-baseline]
key-files:
  created:
    - src/test/java/org/ctc/e2e/WalkoverE2ETest.java
  modified:
    - src/main/resources/static/admin/css/admin.css
    - src/main/resources/templates/admin/matchday-detail.html
    - src/main/resources/templates/site/standings.html
key-decisions:
  - Forfeiter label uses explicit UUID .equals comparison (walkoverTeam.id.equals(home/away id)) — value not object identity (RESEARCH Pitfall 3)
  - Public standings "(w/o)" span appended INLINE with no surrounding whitespace so StandingsPageGenerator byte-identical baseline holds when hasWalkover is false
  - WalkoverE2ETest seeds its own test-prefixed matchday/match (E2E WO Home/Away) — no real data, no shared fixture collision
requirements-completed: [WO-03-standings, WO-04-e2e]
duration: ~30 min
completed: 2026-05-30
---

# Phase 109 Plan 04: Walkover Standings Label + E2E Summary

The "w/o" marker now renders in both standings surfaces: the admin matchday-detail shows an inline `w/o` next to the forfeiter team (home or away), and the public standings table shows a `(w/o)` note next to a team whose `TeamStanding.hasWalkover` flag is set. An enabled Playwright E2E test proves the full admin-marks-walkover → "w/o"-visible flow.

**Tasks:** 3 | **Files:** 3 modified + 1 created

## What was built

- **Task 1 — admin matchday-detail + CSS:** Appended a `.match-wo` rule to `admin.css` (`font-size: 13px; color: var(--text-dim); font-style: italic;`, mirroring `.match-bye`, which stays unchanged at count 1). In `matchday-detail.html` the home and away team cells each carry a `<span class="match-wo">w/o</span>` guarded by `match.walkoverTeam != null and match.walkoverTeam.id.equals(...)` (away analog also null-checks `match.awayTeam`).
- **Task 2 — public standings:** Inline `<span th:if="${s.hasWalkover}" class="text-dim" th:text="' (w/o)'"></span>` after the team-name span, reusing the existing `text-dim` class (no new site CSS).
- **Task 3 — WalkoverE2ETest:** `@Tag("e2e")`, enabled (no `@Disabled`). Seeds a self-contained two-team matchday, navigates the match edit form, selects the away team in the `#walkoverTeamId` dropdown, saves, then asserts `.match-wo` reads `w/o` on the matchday-detail page.

## Verification

- `./mvnw -Pe2e -Dtest=StandingsPageGeneratorTest -Dit.test=WalkoverE2ETest verify` → StandingsPageGeneratorTest 9/9 (byte-identical baseline holds), WalkoverE2ETest 1/1 green.
- `grep -c .match-bye` = 1 (unchanged); `.match-wo` present once.
- `./mvnw clean test-compile` succeeds.

## Deviations from Plan

**[Rule 1 - Correctness] Standings span placed inline, not on its own line** — Found during: Task 3 verification. The plan said "after the `${s.team.name}` span" on a new line; doing so left a stray whitespace-only line in the rendered output and broke `StandingsPageGeneratorTest.givenLeagueOnlySeason_whenGenerate_thenOutputIsByteIdenticalToBaseline` (the public standings template is byte-compared against a golden baseline by the site generator). Fix: append the conditional span directly after `</span>` with no whitespace, so Thymeleaf removes the whole element with nothing left behind when `hasWalkover` is false — output stays byte-identical. Same DOM, same class, same `(w/o)` text. Verified by the now-green generator test.

**Total deviations:** 1 (byte-identical baseline preservation). **Impact:** none on requirements; both rendered cases (walkover / no-walkover) match intent.

## Self-Check: PASSED

WO-03 standings half + WO-04 E2E proof complete. The graphics half of WO-03 (match-results / lineup / provisional "w/o" badge) remains for plan 109-05 (Wave 5, visual checkpoint).
