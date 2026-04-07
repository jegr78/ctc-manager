---
phase: 13-layer-cleanup-recovery
verified: 2026-04-06T18:45:00Z
status: human_needed
score: 3/4 must-haves verified (SC4 needs human)
human_verification:
  - test: "Open admin UI in a browser against the dev profile and navigate to: /admin/standings, /admin/power-rankings, /admin/playoff, /admin/team-card, and the CSV import page"
    expected: "All pages render without errors. Standings page correctly shows Buchholz-sorted standings for Swiss-format seasons. No 500 errors or Thymeleaf binding exceptions."
    why_human: "SC4 'All existing admin UI pages render correctly with unchanged behavior' requires a running server. Cannot verify Thymeleaf template binding to changed service return types (inner records, new method signatures) programmatically."
---

# Phase 13: Layer Cleanup Recovery Verification Report

**Phase Goal:** Re-apply controller service delegation and domain DTO decoupling lost by worktree file clobber
**Verified:** 2026-04-06T18:45:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (Roadmap Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Domain services (org.ctc.domain.service) have zero imports from org.ctc.admin.dto | VERIFIED | `grep -rn "import org.ctc.admin.dto" src/main/java/org/ctc/domain/service/` returns zero matches (exit code 1). RaceService/RaceGraphicService import `org.ctc.admin.service` (not `.dto`) and are explicitly out of scope per plan. |
| 2 | StandingsController, PowerRankingsController, PlayoffController, TeamCardController, CsvImportController inject only services, no repositories | VERIFIED | `grep -rn "import.*Repository"` on all 5 controller files returns zero matches. All 5 controllers confirmed to use service delegation only. |
| 3 | Buchholz calculation and Swiss-system sorting logic lives in StandingsService, not StandingsController | VERIFIED | `standingsService.calculateStandingsWithBuchholz(season.getId())` called at line 47 of StandingsController. Method `calculateStandingsWithBuchholz(UUID seasonId)` confirmed at line 59 of StandingsService. Inline Buchholz/sort block removed. |
| 4 | All existing admin UI pages render correctly with unchanged behavior | HUMAN_NEEDED | Requires running server to verify Thymeleaf template binding with refactored service return types (inner records, primitive parameters). Cannot verify programmatically. |

**Score:** 3/4 truths verified (SC4 pending human check)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/org/ctc/domain/service/SeasonManagementService.java` | findActiveSeason(), findByIdOptional(), save() with primitives | VERIFIED | `findActiveSeason` at line 58, `findByIdOptional` at line 65, `save(UUID id, String name, int year, ...)` at line 92 |
| `src/main/java/org/ctc/domain/service/TeamManagementService.java` | findSeasonTeamById(), findSeasonTeamsBySeasonId(), SeasonDriverGroup inner record | VERIFIED | Methods at lines 208, 217. `record SeasonDriverGroup(Season season, Map<Team, List<Driver>> driversByTeam)` at line 41 |
| `src/main/java/org/ctc/domain/service/PlayoffService.java` | findRoundById() | VERIFIED | `findRoundById` at line 227 |
| `src/main/java/org/ctc/admin/controller/StandingsController.java` | Clean controller with service-only delegation | VERIFIED | `seasonManagementService` at line 25, `calculateStandingsWithBuchholz` at line 47. Zero Repository imports. |
| `src/main/java/org/ctc/domain/service/PlayoffSeedingService.java` | SeedEntry inner record | VERIFIED | `public record SeedEntry(UUID matchupId, int slot, UUID teamId, Integer seedNumber)` at line 173 |
| `src/main/java/org/ctc/domain/service/MatchdayService.java` | MatchdayData inner record | VERIFIED | `public record MatchdayData(UUID id, String label, int sortIndex)` at line 34. JSON field names identical to previous MatchdayDto. |
| `src/main/java/org/ctc/domain/service/CarService.java` | save() with primitives, zero admin.dto imports | VERIFIED | No `import org.ctc.admin.dto` present |
| `src/main/java/org/ctc/domain/service/TrackService.java` | save() with primitives, zero admin.dto imports | VERIFIED | No `import org.ctc.admin.dto` present |
| `src/main/java/org/ctc/domain/service/DriverService.java` | save() with primitives (incl. aliases), zero admin.dto imports | VERIFIED | No `import org.ctc.admin.dto` present |
| `src/main/java/org/ctc/domain/service/RaceScoringService.java` | save() with primitives, zero admin.dto imports | VERIFIED | No `import org.ctc.admin.dto` present |
| `src/main/java/org/ctc/domain/service/MatchScoringService.java` | save() with primitives, zero admin.dto imports | VERIFIED | No `import org.ctc.admin.dto` present |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| StandingsController | StandingsService.calculateStandingsWithBuchholz() | service method call for Swiss format | WIRED | Line 47: `standingsService.calculateStandingsWithBuchholz(season.getId())` inside `if (season.getFormat() == SeasonFormat.SWISS)` branch |
| StandingsController | SeasonManagementService | findActiveSeason/findByIdOptional/findAll | WIRED | Lines 42–43: `seasonManagementService.findByIdOptional(parsedId)` and `seasonManagementService.findActiveSeason()`. Line 57: `seasonManagementService.findAll()` |
| PlayoffController | PlayoffService.findRoundById() | service delegation replacing direct repo access | WIRED | Lines 199, 211, 223: `playoffService.findRoundById(roundId)` (3 call sites) |
| PlayoffController | PlayoffSeedingService.saveSeed(UUID, List<SeedEntry>) | SeedForm mapped to SeedEntry in controller | WIRED | Line 137: `.map(e -> new PlayoffSeedingService.SeedEntry(e.getMatchupId(), e.getSlot(), e.getTeamId(), e.getSeedNumber()))` |
| MatchdayController | MatchdayService.getMatchdaysBySeason() | returns List<MatchdayData> with same JSON field names | WIRED | Lines 175, 181: `List<MatchdayService.MatchdayData>` and `ResponseEntity<MatchdayService.MatchdayData>` return types |
| CarController | CarService.save(UUID, String, String) | primitive extraction from CarForm | WIRED | Line 70: `carService.save(carForm.getId(), carForm.getManufacturer(), carForm.getName())` |

### Data-Flow Trace (Level 4)

Services render dynamic data from repositories. Spot-checked representative cases:

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| StandingsController | `standings` | `standingsService.calculateStandingsWithBuchholz(season.getId())` | Yes — StandingsService queries DB via repositories | FLOWING |
| MatchdayController JSON API | `List<MatchdayData>` | `matchdayRepository.findBySeasonIdOrderBySortIndexAsc(seasonId)` | Yes — DB query in MatchdayService | FLOWING |
| TeamCardController | `seasonTeams` | `teamManagementService.findSeasonTeamsBySeasonId(...)` → `seasonTeamRepository.findBySeasonId(...)` | Yes — DB query through service | FLOWING |

### Behavioral Spot-Checks

Step 7b: SKIPPED for most checks — app requires running server. All code paths verified to exist and be wired. Test suite (795 tests) confirmed passing per SUMMARY documentation and commit history.

| Behavior | Check | Status |
|----------|-------|--------|
| All 11 phase commits exist in git log | `git log --oneline a70b9f7 c4e68d7 9fd7ef4 50f1f87 44a5e25 4d6a0ef 5406747 315889a 2063212 99364c7 46030d1` | PASS — all 11 commits found |
| Zero Repository imports in 5 controllers | grep on 5 files | PASS — no matches |
| Zero admin.dto imports in 9 services | grep on 9 service files | PASS — exit code 1 (no matches) |
| calculateStandingsWithBuchholz wired | grep on StandingsController | PASS — line 47 confirmed |
| Inner records exist | grep on 3 service files | PASS — SeedEntry, SeasonDriverGroup, MatchdayData all found |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| ARCH-01 | Plans 02, 03 | Domain Services importieren keine Admin DTOs mehr | SATISFIED | Zero `import org.ctc.admin.dto` in all 9 in-scope services confirmed. RaceService/RaceGraphicService explicitly out of scope (pre-existing, not Phase 7 targets). |
| ARCH-02 | Plan 01 | Alle 5 Controller nutzen Services statt direkte Repository-Injections | SATISFIED | Zero Repository imports in all 5 target controllers confirmed. |
| FEAT-02 | Plan 01 | StandingsController enthaelt keine Business-Logik mehr — Buchholz-Berechnung in StandingsService | SATISFIED | `calculateStandingsWithBuchholz()` in StandingsService called from controller. Inline Buchholz block removed. |

Note: REQUIREMENTS.md still shows `[ ]` (Pending) for ARCH-01, ARCH-02, and FEAT-02 — the status markers were not updated as part of this phase. This is a documentation housekeeping item, not an implementation gap.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `StandingsController.java` | 32 | `// TODO: Alltime-Standings muessen cross-season MatchScoring-Aggregation unterstuetzen` | INFO | Intentional placeholder per plan ("Keep the `// TODO` placeholder for alltime standings, do NOT restore calculateAlltimeStandings per Pitfall 5"). FEAT-01 is explicitly deferred to Phase 15. |

No blockers or warnings found. The TODO is a known, intentional placeholder confirmed by the plan and by Phase 15 being the designated delivery phase for FEAT-01.

### Human Verification Required

#### 1. Admin UI Rendering Verification

**Test:** Start dev server with `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` and navigate to these pages:
- `http://localhost:9090/admin/standings` (with a Swiss-format season active)
- `http://localhost:9090/admin/power-rankings`
- `http://localhost:9090/admin/playoff`
- `http://localhost:9090/admin/team-card`
- `http://localhost:9090/admin/import` (CSV import page)

**Expected:** All pages load without 500 errors or Thymeleaf binding exceptions. The standings page shows Buchholz-sorted standings for Swiss-format seasons. The team detail page shows drivers grouped correctly using the new `SeasonDriverGroup` inner record.

**Why human:** SC4 "all existing admin UI pages render correctly" requires a running server. Inner records (SeasonDriverGroup, MatchdayData, SeedEntry) changed the return types of service methods — Thymeleaf template expressions referencing these types must be verified to produce correct HTML output. Programmatic grep cannot detect template binding mismatches.

### Gaps Summary

No gaps found. All 3 programmatically-verifiable success criteria are met:
- ARCH-01: 9 in-scope domain services have zero admin.dto imports
- ARCH-02: 5 target controllers have zero repository injections
- FEAT-02: Buchholz/Swiss sorting delegated to StandingsService

One success criterion (SC4: UI rendering) requires human verification — the phase is classified as `human_needed` pending that check.

---

_Verified: 2026-04-06T18:45:00Z_
_Verifier: Claude (gsd-verifier)_
