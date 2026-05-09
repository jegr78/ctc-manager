---
phase: 62-public-site-phases-groups
plan: 04
subsystem: sitegen
type: execute
wave: 5
tags:
  - sitegen
  - team-profile
  - driver-profile
  - phase-breakdown
  - tdd
requires:
  - .planning/REQUIREMENTS.md#UI-02
  - .planning/REQUIREMENTS.md#UI-05
  - "phase-62 plan-00 PhaseBreakdownEntry record + helper extraction"
  - "phase-62 plan-01 StandingsService deterministic-sort tiebreaker"
provides:
  - "Phase-aware TeamProfilePageGenerator emitting conditional Phase Breakdown section per D-13/D-14"
  - "Phase-aware DriverProfilePageGenerator emitting per-phase result sections per D-15"
  - "templates/site/team-profile.html with showPhaseBreakdown flag + Phase Breakdown table"
  - "templates/site/driver-profile.html with conditional legacy + per-phase result sectioning"
  - "SC4 byte-identity baselines for single-LEAGUE team-profile and driver-profile (UUID-tolerant)"
  - "D-16 enforcement: single team-profile / driver-profile URL preserved per (season, entity)"
affects:
  - "src/main/java/org/ctc/sitegen/TeamProfilePageGenerator.java (rewritten with showPhaseBreakdown computation; injected PhaseTeamRepository for PLAYOFF participation fallback)"
  - "src/main/java/org/ctc/sitegen/DriverProfilePageGenerator.java (rewritten with resultsByPhase LinkedHashMap; injected RaceLineupRepository + SeasonPhaseService)"
  - "src/main/resources/templates/site/team-profile.html (Phase Breakdown section appended inline after Drivers)"
  - "src/main/resources/templates/site/driver-profile.html (legacy Race History gated on !showPhaseBreakdown; per-phase sections added)"
  - "src/test/java/org/ctc/sitegen/SiteGeneratorBaselineCaptureTest.java (added captureLeagueOnlyTeamAndDriverProfileBaselines @Disabled @Test)"
tech-stack:
  added: []
  patterns:
    - "Server-side feature flags into Thymeleaf (CLAUDE.md keep-templates-lean) — showPhaseBreakdown / phaseBreakdown / resultsByPhase / phaseHeadings"
    - "Inline-conditional <div th:if=...> placed adjacent to preceding closing tag (no leading whitespace) to preserve SC4 byte-identity when the flag is false"
    - "RaceLineup-as-Source-of-Truth (CLAUDE.md feedback_racelineup_source_of_truth) — phase participation detection for drivers uses RaceLineup, not RaceResult, because PLAYOFF races have lineups but no result rows in the test fixture"
    - "PhaseTeam-roster fallback for PLAYOFF participation detection on team-profile (autoSeedBracket creates PhaseTeam rows for seeded playoff teams; calculateStandings returns empty since PLAYOFF has no Match data)"
    - "UUID-tolerant byte-identity assertion (regex normalization of all 8-4-4-4-12 hex strings) — TestDataService creates teams with random UUIDs that flow into /uploads/teams/{uuid}/ logo URLs; pre-existing non-determinism orthogonal to Phase 62 scope"
    - "LinkedHashMap insertion-order PHASE_ORDER (REGULAR -> PLAYOFF -> PLACEMENT) drives canonical heading order on driver-profile per D-15"
    - "Flyway clean+migrate in @BeforeAll for cross-test-class DB isolation (Plan 1/2/3 pattern)"
key-files:
  created:
    - src/test/java/org/ctc/sitegen/TeamProfilePageGeneratorTest.java
    - src/test/java/org/ctc/sitegen/DriverProfilePageGeneratorTest.java
    - src/test/resources/sitegen/baseline/single-league-team-profile.html
    - src/test/resources/sitegen/baseline/single-league-driver-profile.html
  modified:
    - src/main/java/org/ctc/sitegen/TeamProfilePageGenerator.java
    - src/main/java/org/ctc/sitegen/DriverProfilePageGenerator.java
    - src/main/resources/templates/site/team-profile.html
    - src/main/resources/templates/site/driver-profile.html
    - src/test/java/org/ctc/sitegen/SiteGeneratorBaselineCaptureTest.java
decisions:
  - "Phase Breakdown PLAYOFF entry sourced from PhaseTeamRepository.findByPhaseId(playoff). Reason: TestDataService creates Match data only for REGULAR-phase round-robin races; PLAYOFF uses PlayoffMatchup-bound races and StandingsService.calculateStandings filters out 0-played teams. PlayoffSeedingService.autoSeedBracket DOES create PhaseTeam rows for seeded playoff teams (verified at PlayoffSeedingService.java:227-231). Phase 62 scope emits 'Top {N}' summary; bracket-result strings ('SF exit', 'Champion') deferred to Plan 7 per plan's stated Open Question."
  - "Driver-profile per-phase participation detection sources from RaceLineup, NOT RaceResult. Reason: TestDataService.createPlayoffRaces creates Race + RaceLineup for PLAYOFF semifinals but NO RaceResult rows. RaceLineup is the canonical source of truth per CLAUDE.md (feedback_racelineup_source_of_truth)."
  - "Per-phase result table emits even when the RaceResult list is empty (e.g. driver participated in PLAYOFF but no result rows). The empty <tbody> is acceptable for Phase 62 scope; downstream can populate PLAYOFF results in a future fixture/scoring extension."
  - "byte-identity assertion uses UUID-normalization regex. Reason: Hibernate @GeneratedValue(strategy=GenerationType.UUID) assigns random UUIDs on every TestDataService.seed() run; team UUIDs flow into team.logoUrl ('/uploads/teams/{uuid}/{shortName}.png') and end up in the rendered <img src=...>. This is pre-existing UUID non-determinism orthogonal to Phase 62 scope (parallels Plan 0's documented non-determinism for tied-team standings ordering, which Plan 1 fixed via deterministic-sort tiebreaker; UUIDs cannot be deterministically ordered without changing the entity ID strategy)."
  - "Inline-conditional placement of <div class=\"section section-gap\" th:if=\"\${showPhaseBreakdown}\"> immediately adjacent to preceding </div> (no leading whitespace) preserves SC4 byte-identity. Same pattern as Plan 1 standings.html and Plan 2 matchdays.html. Putting the conditional on its own indented line leaves a blank '    \\n' line in the false-branch output, breaking byte-identity."
  - "D-14 invariant preserved verbatim: TeamProfilePageGenerator.standings is still computed via standingsService.calculateStandings(regularPhase.getId(), null) — combined-view standing — and threaded into the existing 'Record' section. The Phase Breakdown section is purely additive."
  - "D-16 invariant preserved: TeamProfilePageGenerator and DriverProfilePageGenerator emit a SINGLE file per (season, entity) at the existing URL pattern. No phase-suffixed variants like adr-regular.html or adr-driver01-playoff.html. Phase context lives in the page section, not the URL."
  - "RaceLineup-as-Source-of-Truth pattern (lineupDrivers / driversToShow logic at TeamProfilePageGenerator.java lines 109-130) is byte-identical to pre-Plan-4 — verified via git diff search for the relevant pattern lines."
  - "Edge case fallback: when season has >=2 phases but the entity (team OR driver) only participated in ONE of them, showPhaseBreakdown is force-set to false. This keeps SC4 byte-identity for those edge-case entities — the page renders identically to today's single-phase output. Without this fallback, a one-row Phase Breakdown table would clutter the page with no useful comparison."
metrics:
  duration: "~50 minutes"
  completed: 2026-05-06
  tasks: 2
  commits: 3
  files-created: 4
  files-modified: 5
  jacoco-line-coverage: 85.80%
  test-count: 1211
  test-skipped: 4
  test-failures: 0
---

# Phase 62 Plan 04: Phase-Breakdown Sections on Team/Driver Profiles (D-13/D-14/D-15)

This plan adds the Phase Breakdown section to `team-profile.html` (D-13/D-14) and per-phase results sectioning to `driver-profile.html` (D-15). Both are gated by a `showPhaseBreakdown` server-side flag — true ONLY when the season has &ge;2 phases AND the entity participated in &ge;2 of them. When false, both pages render byte-identical to today (SC4 invariant). Single URL per (season, entity) preserved per D-16 — no per-phase URL forks.

## Files Modified (5) + Created (4)

| File | Type | Notes |
|------|------|-------|
| `src/test/java/org/ctc/sitegen/TeamProfilePageGeneratorTest.java` | NEW | 5 test methods: SC4 byte-identity (UUID-tolerant), D-13 Phase Breakdown visibility, D-14 standings panel, D-16 single-URL |
| `src/test/java/org/ctc/sitegen/DriverProfilePageGeneratorTest.java` | NEW | 6 test methods: SC4 byte-identity, D-15 per-phase headings, D-15 results sectioning, D-16 single-URL, REGULAR-PLAYOFF-PLACEMENT ordering |
| `src/test/resources/sitegen/baseline/single-league-team-profile.html` | NEW | Pre-Phase-62 captured Season 2026 ADR team-profile (no Phase Breakdown markers) |
| `src/test/resources/sitegen/baseline/single-league-driver-profile.html` | NEW | Pre-Phase-62 captured Season 2026 ADR_Driver01 driver-profile (no per-phase headings) |
| `src/main/java/org/ctc/sitegen/TeamProfilePageGenerator.java` | MOD | `generate` extended with phaseBreakdown computation + PhaseTeam-PLAYOFF fallback; PhaseTeamRepository injected |
| `src/main/java/org/ctc/sitegen/DriverProfilePageGenerator.java` | MOD | `generate` extended with resultsByPhase LinkedHashMap; RaceLineupRepository + SeasonPhaseService injected |
| `src/main/resources/templates/site/team-profile.html` | MOD | Phase Breakdown section appended inline after Drivers (no leading whitespace) |
| `src/main/resources/templates/site/driver-profile.html` | MOD | Legacy Race History gated on !showPhaseBreakdown; per-phase sections added |
| `src/test/java/org/ctc/sitegen/SiteGeneratorBaselineCaptureTest.java` | MOD | Added captureLeagueOnlyTeamAndDriverProfileBaselines @Disabled @Test |

## Server-Side Flags Exposed to Templates

### Team-Profile

| Flag | Type | Source | Condition |
|------|------|--------|-----------|
| `showPhaseBreakdown` | boolean | TeamProfilePageGenerator | (season has &ge;2 phases) AND (team has &ge;2 phase entries in phaseBreakdown list) |
| `phaseBreakdown` | List&lt;PhaseBreakdownEntry&gt; | TeamProfilePageGenerator | empty when showPhaseBreakdown=false; otherwise one entry per participated phase ordered by sortIndex |
| `standing` | TeamStanding | TeamProfilePageGenerator | combined-view standing from `calculateStandings(REGULAR, null)` — UNCHANGED (D-14) |
| `drivers` | List&lt;DriverEntry&gt; | TeamProfilePageGenerator | RaceLineup-source-of-truth pattern preserved verbatim |

### Driver-Profile

| Flag | Type | Source | Condition |
|------|------|--------|-----------|
| `showPhaseBreakdown` | boolean | DriverProfilePageGenerator | (season has &ge;2 phases) AND (driver has lineups in &ge;2 phases) |
| `resultsByPhase` | LinkedHashMap&lt;PhaseType, List&lt;RaceResult&gt;&gt; | DriverProfilePageGenerator | empty when showPhaseBreakdown=false; otherwise REGULAR -> PLAYOFF -> PLACEMENT canonical order |
| `phaseHeadings` | Map&lt;PhaseType, String&gt; | DriverProfilePageGenerator | static "Regular Season Results" / "Playoff Results" / "Placement Phase Results" |
| `results` | List&lt;RaceResult&gt; | DriverProfilePageGenerator | flat list — used by legacy Race History when showPhaseBreakdown=false |

## SC4 Byte-Identity Verification

Both byte-identity tests pass:

- `TeamProfilePageGeneratorTest.givenLeagueOnlySeasonTeam_whenGenerate_thenLegacyByteIdentical` — UUID-normalized comparison of `/season/2026-4-regular-season/team/adr.html` against captured baseline
- `DriverProfilePageGeneratorTest.givenLeagueOnlySeasonDriver_whenGenerate_thenLegacyByteIdentical` — direct byte-for-byte comparison of `/season/2026-4-regular-season/driver/adr-driver01.html` against captured baseline

The team-profile assertion uses regex normalization of all UUID strings (8-4-4-4-12 hex pattern) because TestDataService re-creates teams with random UUIDs each seed run, and team UUIDs flow into the team-logo URL (`/uploads/teams/{uuid}/{shortName}.png`). This is pre-existing UUID non-determinism unrelated to Phase 62 scope and is consistent with Plan 0's documented similar issue for tied-standings ordering.

## RaceLineup Pattern Preservation

The RaceLineup-as-Source-of-Truth pattern in `TeamProfilePageGenerator.generate` (lines 109-130 — `lineupDrivers` filter + `driversToShow` fallback to SeasonDriver) is byte-identical to pre-Plan-4. Verification:

```bash
git diff HEAD~1 src/main/java/org/ctc/sitegen/TeamProfilePageGenerator.java | grep -E '^[-+].*lineupDrivers|^[-+].*driversToShow'
# (returns empty — both patterns unchanged)
```

The lineup-source-of-truth logic remained byte-identical; only the surrounding code (phaseBreakdown computation) was added.

## D-16 Invariant Verification

Both helpers emit a SINGLE file per (season, entity) with the canonical URL pattern. No phase-suffixed variants are generated. Two tests assert this invariant:

- `TeamProfilePageGeneratorTest.givenMultiPhaseSeasonTeam_whenGenerate_thenSingleProfileUrl` — asserts `adr-regular.html`, `adr-playoff.html`, `adr-placement.html` do NOT exist; `adr.html` DOES exist
- `DriverProfilePageGeneratorTest.givenMultiPhaseSeasonDriver_whenGenerate_thenSingleProfileUrl` — same for driver-profile

## Phase Breakdown Section Behavior

For the multi-phase Season 2023 (REGULAR-GROUPS + PLAYOFF) ADR team:
- REGULAR entry: `"Regular: <rank>{ordinal} place, <points> pts"` from `calculateStandings(regularPhase, null)`
- PLAYOFF entry: `"2023 Playoffs: Top 4"` from `phaseTeamRepository.findByPhaseId(playoff).size()` count

For PLAYOFF, bracket-result strings ("SF exit", "F exit", "Champion") are deferred to Plan 7 per the plan's stated Open Question. Phase 62 scope emits the simpler "Top {N}" summary.

## Per-Phase Sectioning on Driver-Profile

For the multi-phase Season 2023 ADR_Driver01:
- `<h3 class="section-title">Regular Season Results</h3>` followed by results table (REGULAR-phase RaceResult rows)
- `<h3 class="section-title">Playoff Results</h3>` followed by results table (PLAYOFF-phase RaceResult rows; empty in test fixture because TestDataService creates PLAYOFF Race+Lineup but no RaceResult — empty table is acceptable for Phase 62 scope)

The legacy `<h2 class="section-title">Race History — ...</h2>` heading is suppressed via `th:if="${!showPhaseBreakdown && ...}"`.

## JaCoCo Line Coverage

```
[INFO] Tests run: 1211, Failures: 0, Errors: 0, Skipped: 4
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
```

Line coverage: **85.80%** (5807 covered / 6768 total) — well above the 82% project minimum (CLAUDE.md constraint).

## Commits in chronological order

| # | SHA | Message |
|---|-----|---------|
| 1 | `68b44a0` | `test(62-04): capture pre-Phase-62 team-profile and driver-profile baselines for SC4` |
| 2 | `2f1a8c9` | `test(62-04): add failing Team/DriverProfilePageGeneratorTest (TDD-RED) for D-13/D-14/D-15` |
| 3 | `4860eab` | `feat(62-04): conditional Phase Breakdown section in team/driver profiles (D-13/D-14/D-15, TDD-GREEN)` |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] UUID non-determinism in team logo URLs breaks naive byte-identity assertion**

- **Found during:** Task 1 (TDD-RED — initial byte-identity test failed even before any template change)
- **Issue:** `TestDataService.copyDemoLogos` sets `team.logoUrl = "/uploads/teams/{team.id}/{shortName}.png"`. Team IDs are `@GeneratedValue(strategy=GenerationType.UUID)` and assigned at save time, so the logoUrl differs between fixture seed runs. The captured baseline contains UUID `a6c8696a-...`; subsequent runs have different UUIDs. This is the SAME class of pre-existing non-determinism Plan 0 documented for tied-team standings ordering, but for team UUIDs in URLs.
- **Fix:** UUID-normalization helper `normalizeUuids(String)` in `TeamProfilePageGeneratorTest` replaces all 8-4-4-4-12 hex strings with `00000000-0000-0000-0000-000000000000` before assertion. This is robust against future UUID-bearing markup additions while still enforcing structural byte-identity.
- **Files modified:** `src/test/java/org/ctc/sitegen/TeamProfilePageGeneratorTest.java`
- **Commit:** `2f1a8c9`
- **Test impact:** All 5 TeamProfile tests pass; UUID normalization is scoped to the byte-identity test only (other tests work directly on Document/text).

**2. [Rule 2 - Critical Functionality] PLAYOFF participation detection requires PhaseTeam fallback**

- **Found during:** Task 2 (TDD-GREEN — after initial implementation, the multi-phase Phase Breakdown test failed with `phaseBreakdown.size() == 1` — only REGULAR was detected)
- **Issue:** `StandingsService.calculateStandings(playoff.getId(), null)` returns empty because (a) PLAYOFF has no Match rows (PLAYOFF uses PlayoffMatchup-bound Race rows, not Match rows), and (b) calculateStandings filters out teams with `played == 0`. Without an alternate participation source, no team would ever get a PLAYOFF entry in their breakdown.
- **Fix:** Inject `PhaseTeamRepository` and add a fallback branch: if standings-lookup fails for PLAYOFF, check `phaseTeamRepository.findByPhaseId(playoff.getId())`. PlayoffSeedingService.autoSeedBracket creates PhaseTeam rows for the seeded playoff teams (verified at PlayoffSeedingService.java:227-231), making this the canonical participation source. The summary becomes "Top {N}" where N = playoff bracket size.
- **Files modified:** `src/main/java/org/ctc/sitegen/TeamProfilePageGenerator.java`
- **Commit:** `4860eab`
- **Open Question for Plan 7:** Bracket-result strings ("SF exit", "F exit", "Champion") deferred to Plan 7 per the plan's own stated Open Question.

**3. [Rule 2 - Critical Functionality] Driver phase participation requires RaceLineup detection**

- **Found during:** Task 2 (TDD-GREEN — after initial implementation, the multi-phase per-phase headings test failed with `resultsByPhase.size() == 1` — only REGULAR detected)
- **Issue:** `TestDataService.createPlayoffRaces` creates `Race` + `RaceLineup` rows for PLAYOFF semifinals but does NOT create `RaceResult` rows. The initial implementation filtered `results` (RaceResult list) by phase type, which produced 0 PLAYOFF entries → driver fell back to showPhaseBreakdown=false.
- **Fix:** Inject `RaceLineupRepository` and detect phase participation via `findByDriverIdAndRaceMatchdaySeasonId` mapped to phase types. This is consistent with CLAUDE.md "RaceLineup is Source of Truth" pattern (feedback_racelineup_source_of_truth). Per-phase result table emits even when the RaceResult list is empty for that phase.
- **Files modified:** `src/main/java/org/ctc/sitegen/DriverProfilePageGenerator.java`
- **Commit:** `4860eab`

**4. [Rule 1 - Bug] Inline-conditional placement of new section needed for SC4 byte-identity**

- **Found during:** Task 2 (TDD-GREEN — after adding the Phase Breakdown section in team-profile.html, the SC4 byte-identity test for LEAGUE-only fixture failed because Thymeleaf left a blank `    \n` line where the conditional `<div th:if="${showPhaseBreakdown}">` had been when the flag was false)
- **Issue:** Standard indentation of `    <div class="section section-gap" th:if="${showPhaseBreakdown}">` leaves leading whitespace + newline in the rendered output when the flag is false. This injects new bytes vs. the captured baseline.
- **Fix:** Place the conditional `<div th:if="...">` directly adjacent to the preceding closing tag (no leading whitespace) — same pattern Plan 1 used for standings.html `<section><nav th:if=...>` and Plan 2 used for matchdays.html. Result: when th:if=false, Thymeleaf removes the entire element and no whitespace remains.
- **Files modified:** `src/main/resources/templates/site/team-profile.html`, `src/main/resources/templates/site/driver-profile.html`
- **Commit:** `4860eab`

### Authentication Gates

None — pure refactor + test. No external services.

### Departures from Plan Task Action Text

**Task 2 PART C (DriverProfilePageGenerator) — phase headings as static Map field:** The plan suggested computing `phaseHeadings` per-driver iteration. I extracted to a static class-level `Map.of(...)` constant (`PHASE_HEADINGS`) since it never changes. Same with `PHASE_ORDER` (List of REGULAR/PLAYOFF/PLACEMENT). Pure code-cleanup; no functional difference.

**Task 2 PART D (driver-profile.html template) — kept the layout `~{::section}` selector:** The plan's example markup didn't specify the layout fragment selector. I kept the existing `~{::section}` selector (no `<th:block th:fragment=...>` wrapping) for consistency with Plan 1/2/3 templates and to avoid changing the layout fragment-call signature. Functional contract unchanged.

## Branch Verification

Active branch: `gsd/v1.9-season-phases-groups` — unchanged throughout execution. No `git stash`, `git checkout`, `git reset`, `git clean`, or branch switching was used.

## Note for Plan 7 (Release Notes)

Document this user-visible additive behavior change in the release notes (mirrors Plan 1/2/3 release-note entries):

> **Multi-phase team and driver profiles now show per-phase context.** When a season has &ge;2 phases (e.g. REGULAR + PLAYOFF), the team-profile page gains a "Phase Breakdown" section showing the team's standing per phase, and the driver-profile page splits race results into per-phase sections (Regular Season Results, Playoff Results, Placement Phase Results). Single-REGULAR-LEAGUE seasons (today's typical production data shape) render team-profile and driver-profile pages with no behavior change.
>
> The single team-profile / driver-profile URL is preserved per (season, entity) — phase context lives inside the page as a section, not in the URL (D-16). No new files like `team-profile-regular.html` are generated.

## Open Questions for Downstream Plans

- **Bracket-result strings on PLAYOFF Phase Breakdown entries (Plan 7 candidate):** Phase 62 emits `"2023 Playoffs: Top 4"` for PLAYOFF participants. A future plan could derive richer summaries like `"2023 Playoffs: SF exit"` / `"2023 Playoffs: Champion"` by inspecting `PlayoffMatchup` outcomes (winner determination from race results). Requires PLAYOFF results to be populated in fixtures and the matchup-winner derivation logic from PlayoffService.
- **Per-phase RaceResult population in test fixtures (Plan 5/6 candidate):** TestDataService.createPlayoffRaces seeds Race+RaceLineup for PLAYOFF but skips RaceResult. Adding PLAYOFF RaceResult seeding would (a) populate the Playoff Results table on driver-profile (currently empty), (b) enable Top-N PLAYOFF standings for the bracket-result strings above, (c) make Plan 5 alltime cross-phase aggregation (D-19) testable for PLAYOFF contributions.
- **PlayoffMatchup-aware Phase Breakdown (deferred from Phase 62):** A richer Phase Breakdown could surface bracket position ("Round 1 winner", "Semifinalist", "Champion") based on which round the team was eliminated in. Out of scope for Phase 62.

## Plan-size rationale

Plan 4 = 3 atomic commits (1 baseline + 1 RED + 1 GREEN). The 2-task structure was distributed across 3 commits per the plan's intentional baseline-first ordering. Each commit is independently meaningful:

1. `68b44a0` — baseline capture (independent of any source change; could be reverted without breaking subsequent commits)
2. `2f1a8c9` — failing tests + UUID-normalize helper (red-light gate enables clean diff in commit 3)
3. `4860eab` — full implementation (passes all tests, makes the red turn green)

## Self-Check: PASSED

- [x] `src/test/resources/sitegen/baseline/single-league-team-profile.html` exists (6553 bytes, no `Phase Breakdown` marker)
- [x] `src/test/resources/sitegen/baseline/single-league-driver-profile.html` exists (8080 bytes, no `Regular Season Results` / `Playoff Results` / `Placement Phase Results` markers)
- [x] `src/test/java/org/ctc/sitegen/TeamProfilePageGeneratorTest.java` exists with 5 test methods, all required Given-When-Then names present (verified via grep)
- [x] `src/test/java/org/ctc/sitegen/DriverProfilePageGeneratorTest.java` exists with 6 test methods, all required Given-When-Then names present (verified via grep)
- [x] `src/main/java/org/ctc/sitegen/TeamProfilePageGenerator.java` references `showPhaseBreakdown`, `phaseBreakdown`, `lineupDrivers`, `driversToShow`, `calculateStandings(...null)` (verified via grep)
- [x] `src/main/java/org/ctc/sitegen/DriverProfilePageGenerator.java` references `showPhaseBreakdown`, `resultsByPhase`, `getPhase().getPhaseType()` (verified via grep)
- [x] `src/main/resources/templates/site/team-profile.html` contains `th:if="${showPhaseBreakdown}"`, references `phaseBreakdown`, contains literal "Phase Breakdown" (verified via grep)
- [x] `src/main/resources/templates/site/driver-profile.html` contains `resultsByPhase` reference and `phaseHeadings` lookup (verified via grep)
- [x] RaceLineup-as-Source-of-Truth pattern in TeamProfilePageGenerator preserved verbatim — `git diff HEAD~1 -- src/main/java/org/ctc/sitegen/TeamProfilePageGenerator.java | grep -E '^[-+].*lineupDrivers|^[-+].*driversToShow'` returns empty
- [x] D-14 invariant: standings panel still uses `calculateStandings(regularPhase.getId(), null)` — verified via grep
- [x] D-16 invariant: NO per-phase URL forks generated — enforced by tests (`thenSingleProfileUrl`)
- [x] SC4 byte-identity passes for both LEAGUE-only fixtures (UUID-normalized for team-profile)
- [x] All 11 helper tests pass: `./mvnw -Dtest='TeamProfilePageGeneratorTest,DriverProfilePageGeneratorTest' test` (Tests run: 11, Failures: 0, Errors: 0, Skipped: 0)
- [x] All 131 sitegen tests still green: `./mvnw -Dtest='SiteGenerator*,*PageGeneratorTest' test` (Tests run: 131, Failures: 0, Errors: 0, Skipped: 3)
- [x] Full `./mvnw verify` passes: 1211 tests, 0 failures, 4 skipped, BUILD SUCCESS
- [x] JaCoCo line coverage = 85.80% (≥ 82% project minimum)
- [x] Three atomic commits exist on `gsd/v1.9-season-phases-groups`: `68b44a0`, `2f1a8c9`, `4860eab`
- [x] Branch unchanged: `git branch --show-current` returns `gsd/v1.9-season-phases-groups`
- [x] No `git stash`, `git checkout`, `git reset`, or `git clean` used
- [x] No commits modify STATE.md / ROADMAP.md (orchestrator-owned)
- [x] No file deletions in the plan's three commits (`git diff --diff-filter=D --name-only HEAD~3 HEAD` returns empty)
