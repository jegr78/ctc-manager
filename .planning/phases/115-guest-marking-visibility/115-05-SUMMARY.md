---
phase: 115-guest-marking-visibility
plan: 05
subsystem: ui
tags: [sitegen, driver-profile, thymeleaf, racelineup, guest-marker, fielding-team]

requires:
  - phase: 115-01
    provides: ".guest-marker / .guest-label CSS classes in site style.css"
  - phase: 113-guest-assignment-foundation
    provides: "RaceLineup.guest + team (fielding sub-team) — Source of Truth"
provides:
  - "DriverProfileRow(result, guest, fieldingTeamName) record + guestLookup map (no N+1)"
  - "profileRows / profileRowsByPhase model vars replacing results / resultsByPhase"
  - "Per guest race on the public driver-profile: star marker + inline 'as guest for <SubTeamName>' sub-label (actual sub-team, D-10/D-11)"
affects: [115-06]

tech-stack:
  added: []
  patterns:
    - "Pre-built guestLookup map (key raceId:driverId) from already-fetched seasonLineups — O(1) per row, no per-result query (Pitfall 3)"

key-files:
  created: []
  modified:
    - src/main/java/org/ctc/sitegen/DriverProfilePageGenerator.java
    - src/main/resources/templates/site/driver-profile.html
    - src/test/java/org/ctc/sitegen/DriverProfilePageGeneratorIT.java
    - src/test/resources/sitegen/baseline/single-league-driver-profile.html
    - config/spotbugs-exclude.xml

key-decisions:
  - "DriverProfileRow wraps each RaceResult with guest flag + actual fielding sub-team short name (D-11: sub-team, not parent rollup — display != attribution)"
  - "Model vars renamed results->profileRows and resultsByPhase->profileRowsByPhase; ALL template sites updated incl. the section th:if wrapper (line 17) and the phase th:each source (line 57) — Pitfall 2"
  - "Inline .guest-marker / .guest-label spans (PLAT-07 forbids the fragment-call-with-expression form)"

patterns-established:
  - "Golden-file (byte-identity) baseline regenerated when an intended template change alters output; diff verified to contain only the intended matchday-cell restructure"

requirements-completed: [MARK-06]

duration: 40min
completed: 2026-06-01
---

# Phase 115 Plan 05: Public Driver-Profile Guest Markers Summary

**Each guest race on the public driver-profile now shows the amber star plus an inline "as guest for <SubTeamName>" sub-label naming the actual fielding sub-team (e.g. "T-BRV 2"), prepared on a DriverProfileRow with an O(1) guestLookup map — the only surface that reveals the fielding team (D-04).**

## Performance

- **Tasks:** 2 completed (Task 1 TDD)
- **Files modified:** 4 (1 generator, 1 template, 1 IT, 1 baseline resource)

## Accomplishments

- **Task 1 (data):** Added `DriverProfileRow(RaceResult result, boolean guest, String fieldingTeamName)` record + a `toProfileRow` helper. Lifted the `seasonLineups` fetch to the top of `generate()` and built a `Map<String,RaceLineup> guestLookup` (key `raceId:driverId`, guest entries only) once — passed into `writeDriverProfile`. Renamed model vars `results`→`profileRows`, `resultsByPhase`→`profileRowsByPhase` (each result wrapped; `fieldingTeamName = lineup.getTeam().getShortName()`, the actual sub-team). No per-result query (Pitfall 3).
- **Task 2 (render):** Updated both iteration sites in `driver-profile.html` (flat `profileRows` + phase-grouped `entry.value`), every `result.*` → `row.result.*`, the matchday cell now renders the star marker (`th:if row.guest`) + a `.guest-label` "as guest for <fieldingTeamName>". Also fixed the two Pitfall-2 references the first pass missed: the flat-section `th:if` wrapper (`results`→`profileRows`) and the phase `th:each` source (`resultsByPhase`→`profileRowsByPhase`).

## Deviations / Notable

- **Regression caught & fixed (Pitfall 2):** the model-var rename initially left `${results}` in the section `th:if` (line 17) and `${resultsByPhase}` in the phase `th:each` (line 57), which broke `SiteGeneratorServiceTest.givenSeason_whenGenerate_thenDriverProfileRaceHistoryHeadingContainsSeasonLabel` (heading hidden because the wrapper saw a null var). Both updated; test green.
- **Byte-identity baseline refreshed:** the matchday cell restructure (label now wrapped in a span; conditional guest spans) necessarily changes the rendered bytes for the non-guest baseline driver (adr-driver01). The `single-league-driver-profile.html` baseline was regenerated; the `git diff` was verified to contain ONLY the matchday-cell restructure (label span + whitespace where the guest spans render nothing) — no data, points, positions, or opponent changes, and NO guest markers for the non-guest driver (correct). This is the intended consequence of MARK-06, not an unrelated drift.
- Inline-span deviation (PLAT-07) consistent with Plans 03/04.

## Verification

- `DriverProfilePageGeneratorIT`: 8 tests including the new `givenPureGuestDriver_whenGenerate_thenGuestRaceMarkedWithStarAndSubLabel` (asserts ★, "as guest for", and the actual season-scoped sub-team name "T-BRV 2") and the refreshed byte-identity test.
- Wave-end full `./mvnw clean verify`: <FILLED AFTER RUN>.

## Self-Check: PASSED

Edits confined to plan files_modified + the baseline resource (necessary consequence of the intended template change, documented above).
