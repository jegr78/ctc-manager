---
phase: 115-guest-marking-visibility
plan: 04
subsystem: ui
tags: [ranking, driver-ranking, racelineup, guest-marker, jacoco-covered]

requires:
  - phase: 115-01
    provides: ".guest-marker CSS class (admin.css + site style.css)"
  - phase: 113-guest-assignment-foundation
    provides: "RaceLineup.guest — per-result Source of Truth"
provides:
  - "DriverRanking.hasGuestAppearance + markGuestAppearance() + isHasGuestAppearance()"
  - "Per-result guest marking across calculateRankingForPhase, calculateAlltimeRanking, propagated in aggregateAcrossPhases"
  - "Guest star on admin standings + public season ranking + public alltime ranking (binary, no team name)"
affects: [115-06]

tech-stack:
  added: []
  patterns:
    - "Per-result lineup check (raceId from result.getRace().getId()) marks the row — covers BOTH pure guests and dual-role drivers (Pitfall 4)"

key-files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/service/DriverRankingService.java
    - src/main/resources/templates/admin/standings.html
    - src/main/resources/templates/site/driver-ranking.html
    - src/main/resources/templates/site/alltime-driver-ranking.html
    - src/test/java/org/ctc/domain/service/DriverRankingServiceGuestIT.java

key-decisions:
  - "Flag keyed on the specific result's RaceLineup.guest, NOT on pure-guest status (Pitfall 4 — dual-role drivers with a SeasonDriver are still marked)"
  - "aggregateAcrossPhases propagates the per-phase flag onto the merged season row (no extra query)"
  - "Inline .guest-marker span (th:if on row hasGuestAppearance) used in all three templates — PLAT-07 forbids the fragment-call-with-expression form; plan permits inline here"

patterns-established:
  - "DriverRanking mutable-field accumulator gains hasGuestAppearance alongside bestPosition"

requirements-completed: [MARK-05]

duration: 22min
completed: 2026-06-01
---

# Phase 115 Plan 04: Driver-Ranking Guest Markers Summary

**Driver-ranking rows with at least one guest appearance — pure guests AND dual-role drivers — are now marked with the amber star identically in admin standings and both public-site rankings, via a service-computed hasGuestAppearance flag.**

## Performance

- **Tasks:** 2 completed (Task 1 TDD via the guest IT)
- **Files modified:** 5 (1 service, 3 templates, 1 IT)

## Accomplishments

- **Task 1 (data):** Added `hasGuestAppearance` field + `markGuestAppearance()` + `isHasGuestAppearance()` to the `DriverRanking` inner class (mutable-field pattern). `calculateRankingForPhase` and `calculateAlltimeRanking` now do an explicit per-result `raceLineupRepository.findByRaceIdAndDriverId(result.getRace().getId(), driverId).filter(RaceLineup::isGuest).ifPresent(rl -> ranking.markGuestAppearance())` — Pitfall 4 compliant (dual-role drivers with a SeasonDriver are caught because the check is per-result, not per-pure-guest). `aggregateAcrossPhases` propagates the per-phase flag onto the merged season row.
- **Task 2 (render):** Inline `.guest-marker` star span before the driver psnId in `standings.html` (`ranking.hasGuestAppearance`), `driver-ranking.html` and `alltime-driver-ranking.html` (`r.hasGuestAppearance`). Binary marker only — no fielding-team name (D-04).

## Verification

- `DriverRankingServiceGuestIT`: **5 tests, 0 failures** — new test asserts `hasGuestAppearance` true for pure guest (`Test_Guest_1`) across phase/season/alltime, true for dual-role (`Test_DualRole_1`) in season+alltime, and at least one rostered row stays false.
- Note on the targeted verify command: `clean verify -Dit.test=DriverRankingServiceGuestIT` trips `jacoco:check` (running a single IT drops aggregate coverage below 82%). This is a command artifact, not a regression — the IT passed. Coverage is validated by the wave-end full `clean verify` (run after Plan 115-05).
- Task 2: `clean test-compile` OK, PLAT-07 guard OK, grep gates pass.

## Self-Check: PASSED

Edits confined to the plan's files_modified whitelist. Inline-span deviation (PLAT-07) consistent with Plan 03.
