---
phase: 73-backup-export-jackson-mixins-streaming-zip-endpoint
plan: 02
subsystem: database
tags: [spring-data-jpa, entitygraph, hibernate, backup, eager-fetch, lazy-init, reflective-it]

# Dependency graph
requires:
  - phase: 72-backup-wire-contract
    provides: BackupSchema.getExportOrder() returning 24 EntityRef instances over org.ctc.domain.model.*
provides:
  - findAllForBackup() finder on every operative-entity repository (24 in total)
  - @EntityGraph(attributePaths={...}) eager-fetch contract per repository (mapped to the Phase 73-01 MixIn ID-reference shape)
  - BackupRepositoryEntityGraphIT — reflective Failsafe IT that proves the contract at runtime against the dev fixture
affects: [73-03 (BackupExportService dispatches per EntityRef to the new finders), 74 (BackupImportService restores into the same 24 entities), 75 (Replace-All transaction deletes the same 24 tables in FK-reverse order)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Per-repository findAllForBackup() with @EntityGraph + explicit @Query(\"SELECT e FROM Entity e\") — additive, never replaces existing finders"
    - "Reflective IT pattern: Repositories helper + dev-seeded fixture + outside-transaction lazy-init probe via direct field reflection (covers @Getter(AccessLevel.NONE) edge case)"

key-files:
  created:
    - src/test/java/org/ctc/backup/repository/BackupRepositoryEntityGraphIT.java
  modified:
    - src/main/java/org/ctc/domain/repository/CarRepository.java
    - src/main/java/org/ctc/domain/repository/TrackRepository.java
    - src/main/java/org/ctc/domain/repository/RaceScoringRepository.java
    - src/main/java/org/ctc/domain/repository/MatchScoringRepository.java
    - src/main/java/org/ctc/domain/repository/DriverRepository.java
    - src/main/java/org/ctc/domain/repository/PsnAliasRepository.java
    - src/main/java/org/ctc/domain/repository/TeamRepository.java
    - src/main/java/org/ctc/domain/repository/SeasonRepository.java
    - src/main/java/org/ctc/domain/repository/SeasonPhaseRepository.java
    - src/main/java/org/ctc/domain/repository/SeasonPhaseGroupRepository.java
    - src/main/java/org/ctc/domain/repository/PhaseTeamRepository.java
    - src/main/java/org/ctc/domain/repository/SeasonTeamRepository.java
    - src/main/java/org/ctc/domain/repository/SeasonDriverRepository.java
    - src/main/java/org/ctc/domain/repository/PlayoffRepository.java
    - src/main/java/org/ctc/domain/repository/PlayoffRoundRepository.java
    - src/main/java/org/ctc/domain/repository/PlayoffMatchupRepository.java
    - src/main/java/org/ctc/domain/repository/PlayoffSeedRepository.java
    - src/main/java/org/ctc/domain/repository/MatchdayRepository.java
    - src/main/java/org/ctc/domain/repository/MatchRepository.java
    - src/main/java/org/ctc/domain/repository/RaceRepository.java
    - src/main/java/org/ctc/domain/repository/RaceLineupRepository.java
    - src/main/java/org/ctc/domain/repository/RaceResultRepository.java
    - src/main/java/org/ctc/domain/repository/RaceSettingsRepository.java
    - src/main/java/org/ctc/domain/repository/RaceAttachmentRepository.java

key-decisions:
  - "Each finder uses explicit JPQL \"SELECT e FROM Entity e\" to bypass the Spring Data method-name parser (which would otherwise interpret \"ForBackup\" as a property filter)"
  - "Five zero-association repositories (Car, Track, RaceScoring, MatchScoring, Driver) emit @EntityGraph(attributePaths = {}) for contract uniformity — the reflective IT skips them for the size-check, but invokes the finder to confirm bean wiring"
  - "Rule 1 deviation on SeasonRepository: @EntityGraph(attributePaths = {\"cars\", \"tracks\"}) was technically infeasible (MultipleBagFetchException — Hibernate forbids simultaneous JOIN FETCH of two List<> @ManyToMany bags). The graph now lists only {\"cars\"}; Plan 73-03's @Transactional(readOnly=true) service materialises tracks via lazy load inside its session."
  - "IT test class is deliberately NOT @Transactional — otherwise the class-level session would mask lazy-init bugs (a missing attributePath would still pass)"
  - "Reflective getter resolver falls back to direct java.lang.reflect.Field access for Race.homeTeamOverride / awayTeamOverride which use @Getter(AccessLevel.NONE) and are exposed only via the convenience methods getHomeTeam() / getAwayTeam()"

patterns-established:
  - "findAllForBackup() shape: @EntityGraph(attributePaths={...}) + @Query(\"SELECT e FROM Entity e\") + List<Entity> return type — every backup-relevant repository in the project must follow this shape"
  - "Reflective IT pattern for repository contracts: Spring's Repositories helper + non-@Transactional test class + outside-transaction attribute dereference"

requirements-completed: [EXPORT-05]

# Metrics
duration: 50min
completed: 2026-05-11
---

# Phase 73 Plan 02: Per-Entity Repository @EntityGraph Eager-Fetch Contract Summary

**Each of the 24 operative-entity repositories gains an additive `findAllForBackup()` method whose `@EntityGraph(attributePaths={...})` eager-fetches the associations the Phase 73-01 MixIn renders as ID references, plus a reflective Failsafe IT that proves the contract holds at runtime on the dev-seeded fixture.**

## Performance

- **Duration:** ~50 min
- **Started:** 2026-05-11T19:30:00Z
- **Completed:** 2026-05-11T20:24:00Z
- **Tasks:** 2 (both `tdd="true"`)
- **Files modified:** 24 (production repositories) + 1 created (IT test)

## Accomplishments

- Every entity in `BackupSchema.getExportOrder()` (24 entries) now has a working `findAllForBackup()` method that materialises its row aggregate with all Phase 73-01 MixIn-relevant associations eager-fetched in one JPQL query.
- `BackupRepositoryEntityGraphIT` proves the contract at runtime: 2 test methods, 0 failures, 0 errors on the dev fixture, JaCoCo coverage gate held.
- No existing repository method on any of the 24 files was modified — changes are purely additive.
- Discovered and documented (via Rule 1 deviation) a runtime-only Hibernate limitation that RESEARCH §EntityGraph Fetch Map line 450 did not anticipate (`MultipleBagFetchException` on `Season.cars` + `Season.tracks`).

## Task Commits

Each task was committed atomically:

1. **Task 1: Add findAllForBackup() to all 24 entity repositories** — `f9bd543` (feat)
   - 24 files changed, 276 insertions(+), 0 deletions(-)
2. **Task 1 follow-up: Season fix (Rule 1 deviation)** — `69dd3a0` (fix)
   - `SeasonRepository.java`: `@EntityGraph(attributePaths = {"cars"})` instead of `{"cars", "tracks"}` to dodge `MultipleBagFetchException`.
3. **Task 2: BackupRepositoryEntityGraphIT reflective contract test** — `4fcbbfc` (test)
   - 1 file created, 262 insertions(+).

## Files Created/Modified

### Created

- `src/test/java/org/ctc/backup/repository/BackupRepositoryEntityGraphIT.java` — Reflective Failsafe IT with 2 test methods:
  - `givenAllExportOrderEntities_whenInvokeFindAllForBackup_thenNoLazyInitExceptionOnDeclaredAttributePaths`
  - `givenAllExportOrderEntitiesWithFks_whenInspectFindAllForBackupAnnotation_thenEntityGraphHasNonEmptyAttributePaths`

### Modified (24 repository files — all additive)

Per-repository `attributePaths` (as locked by RESEARCH §EntityGraph Fetch Map, except for `SeasonRepository` which deviates — see "Deviations" below):

| Repository | `attributePaths` |
| ---------- | ---------------- |
| `CarRepository` | `{}` (zero-association leaf) |
| `TrackRepository` | `{}` (zero-association leaf) |
| `RaceScoringRepository` | `{}` (zero-association leaf) |
| `MatchScoringRepository` | `{}` (zero-association leaf) |
| `DriverRepository` | `{}` (only `@OneToMany`, ignored by MixIn) |
| `PsnAliasRepository` | `{"driver"}` |
| `TeamRepository` | `{"parentTeam"}` |
| `SeasonRepository` | `{"cars"}` **(deviates from plan — see below)** |
| `SeasonPhaseRepository` | `{"season", "raceScoring", "matchScoring"}` |
| `SeasonPhaseGroupRepository` | `{"phase"}` |
| `PhaseTeamRepository` | `{"phase", "team", "group"}` |
| `SeasonTeamRepository` | `{"season", "team", "successor"}` |
| `SeasonDriverRepository` | `{"season", "driver", "team"}` |
| `PlayoffRepository` | `{"phase"}` |
| `PlayoffRoundRepository` | `{"playoff"}` |
| `PlayoffMatchupRepository` | `{"round", "team1", "team2", "winner", "nextMatchup"}` |
| `PlayoffSeedRepository` | `{"playoff", "team"}` |
| `MatchdayRepository` | `{"phase", "group"}` |
| `MatchRepository` | `{"matchday", "homeTeam", "awayTeam"}` |
| `RaceRepository` | `{"matchday", "match", "track", "car", "playoffMatchup", "homeTeamOverride", "awayTeamOverride"}` |
| `RaceLineupRepository` | `{"race", "driver", "team"}` |
| `RaceResultRepository` | `{"race", "driver"}` |
| `RaceSettingsRepository` | `{"race"}` |
| `RaceAttachmentRepository` | `{"race"}` |

## Decisions Made

- **Explicit JPQL on every finder** — `@Query("SELECT e FROM Entity e")` is mandatory: the method name `findAllForBackup` ends with `ForBackup`, which the Spring Data method-name parser could otherwise mis-interpret as a `forBackup` property filter (RESEARCH §EntityGraph Fetch Map line 442).
- **Empty `@EntityGraph` on the five zero-association leaves** — uniform contract (every repository carries the annotation) simplifies the reflective IT's grep / annotation lookup and documents the intent ("no associations to fetch") at the API surface.
- **IT not `@Transactional`** — a class-level transaction would extend the Hibernate session for the entire test method, silently masking a missing `attributePath` (lazy access would succeed inside the session). The IT class is intentionally non-transactional so each `findAllForBackup()` runs in its own Spring-Data-generated transaction scope; subsequent attribute dereferencing happens outside that scope, exposing any LIE.
- **Reflective field-access fallback** — `Race.homeTeamOverride` and `Race.awayTeamOverride` are annotated `@Getter(AccessLevel.NONE)` (replaced by the convenience methods `getHomeTeam()` / `getAwayTeam()` that delegate through `match` or `playoffMatchup`). The IT's attribute-dereference helper tries the conventional getter first, then falls back to `Class#getDeclaredField` + `setAccessible(true)` so the override fields participate in the lazy-init probe like any other association.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Season.findAllForBackup() `@EntityGraph` reduced from `{"cars", "tracks"}` to `{"cars"}` to avoid Hibernate `MultipleBagFetchException`**

- **Found during:** Task 2 (IT first run)
- **Issue:** RESEARCH §EntityGraph Fetch Map row 8 + line 450 specified `@EntityGraph(attributePaths = {"cars", "tracks"})` on `SeasonRepository.findAllForBackup()`. RESEARCH assumed Hibernate would issue two separate join-table SELECTs. In practice, the `@EntityGraph` translates to a single JPQL `LEFT JOIN FETCH` for both `@ManyToMany` `List<>` bags, which Hibernate rejects with `org.hibernate.loader.MultipleBagFetchException: cannot simultaneously fetch multiple bags: [org.ctc.domain.model.Season.cars, org.ctc.domain.model.Season.tracks]`.
- **Fix:** `SeasonRepository.findAllForBackup()` now declares `@EntityGraph(attributePaths = {"cars"})`. The complement (`tracks`) is materialised by Plan 73-03's `BackupExportService`, which per EXPORT-05 runs under `@Transactional(readOnly=true)` — `season.getTracks()` therefore lazy-loads inside the service's still-open Hibernate session without `LazyInitializationException`.
- **Alternatives considered (rejected):**
  - Convert one of the collections from `List<>` to `Set<>` — silently breaks ~10 callers across `SeasonManagementService`, `RaceFormDataService`, `CarService`, `TrackService` that rely on `List` ordering semantics and `.contains()` performance.
  - Add `@Fetch(FetchMode.SUBSELECT)` on both collections — Hibernate-specific annotation; expands surface beyond Spring Data idioms and would still require Entity modification.
- **Files modified:** `src/main/java/org/ctc/domain/repository/SeasonRepository.java`
- **Verification:** `./mvnw -DskipUTs -Dit.test=BackupRepositoryEntityGraphIT verify` BUILD SUCCESS with `Tests run: 2, Failures: 0, Errors: 0`.
- **Committed in:** `69dd3a0` (separate `fix(73-02)` commit; the deviation rationale is captured in the commit body and inline JavaDoc on `findAllForBackup()`)

**2. [Rule 1 - Bug] Reflective IT getter resolver falls back to direct field access for `@Getter(AccessLevel.NONE)` fields**

- **Found during:** Task 2 (IT second run, after deviation 1 was fixed)
- **Issue:** `Race.homeTeamOverride` / `Race.awayTeamOverride` are annotated `@Getter(AccessLevel.NONE)` and only surfaced through `getHomeTeam()` / `getAwayTeam()` convenience methods (which add `match` / `playoffMatchup` delegation logic). The first IT implementation called `getHomeTeamOverride()` reflectively and failed with `NoSuchMethodException` → `AssertionError: Entity Race has no getter getHomeTeamOverride() for attributePath 'homeTeamOverride'`.
- **Fix:** `readField(Object, String)` in the IT now tries the conventional getter first, then walks the class hierarchy via `getDeclaredField` + `setAccessible(true)` to locate the raw field. The lazy-init probe still works because reflective field access on a Hibernate-uninitialised proxy returns the proxy, and `value.toString()` on the proxy triggers initialisation.
- **Files modified:** `src/test/java/org/ctc/backup/repository/BackupRepositoryEntityGraphIT.java`
- **Verification:** Same BUILD SUCCESS run as deviation 1.
- **Committed in:** `4fcbbfc` (part of the Task 2 commit — the fallback is documented in the helper's JavaDoc)

---

**Total deviations:** 2 auto-fixed (both Rule 1 — runtime bug fixes that the locked plan-table did not anticipate)
**Impact on plan:** Both fixes preserve the plan's intent. Deviation 1 shifts work from the repository layer to the Plan 73-03 service layer; the service already plans to run under `@Transactional(readOnly=true)`, so the additional lazy-load on `Season.getTracks()` is free. Deviation 2 is a test-only robustness improvement. No production surface changes beyond the documented `SeasonRepository` adjustment; no entity changes.

## Issues Encountered

- **Background test run took 8-12 min on first / re-run** (Surefire 1227 tests + Failsafe 31 tests + JaCoCo gate). The IT itself completes in 39 s once Surefire suite passes — the runtime cost lives in the unit suite, not the new IT.
- **No other issues** — the plan's per-entity `attributePaths` table was correct for 23 of 24 entities; only `Season` needed the runtime-driven correction.

## User Setup Required

None — no external service configuration changed by this plan.

## Next Phase Readiness

- **Ready for Plan 73-03 (BackupExportService):** All 24 repositories expose `findAllForBackup()`. The service can constructor-inject each repository and dispatch per `EntityRef.entityClass()` to the corresponding finder. The `@Transactional(readOnly=true) + @EntityGraph` half of the EXPORT-05 contract is now ENFORCED at the repository surface (verified by `BackupRepositoryEntityGraphIT`).
- **Carry-over note for Plan 73-03:** `Season.tracks` is NOT eager-fetched by `SeasonRepository.findAllForBackup()`. The service MUST be `@Transactional(readOnly=true)` (already specified by EXPORT-05) so the lazy load on `season.getTracks()` materialises inside the service's session. A unit / IT regression in Plan 73-03 should assert that the `data/seasons.json` entry for the GROUPS-fixture season includes both `cars` and `tracks` ID references.
- **No blockers.**

## Self-Check: PASSED

- `git log --oneline --all | grep f9bd543` → found
- `git log --oneline --all | grep 69dd3a0` → found
- `git log --oneline --all | grep 4fcbbfc` → found
- `grep -l 'findAllForBackup' src/main/java/org/ctc/domain/repository/*.java | wc -l` → 24
- `grep -L 'findAllForBackup' src/main/java/org/ctc/domain/repository/*.java` → empty (all 24 have it)
- `src/test/java/org/ctc/backup/repository/BackupRepositoryEntityGraphIT.java` → exists
- `./mvnw -DskipUTs -Dit.test=BackupRepositoryEntityGraphIT verify` → BUILD SUCCESS, `Tests run: 2, Failures: 0, Errors: 0`
- JaCoCo `check` gate → PASSED ("All coverage checks have been met.")

---
*Phase: 73-backup-export-jackson-mixins-streaming-zip-endpoint*
*Plan: 02*
*Completed: 2026-05-11*
