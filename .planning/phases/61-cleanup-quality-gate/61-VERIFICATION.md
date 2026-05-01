---
phase: 61-cleanup-quality-gate
verified: 2026-05-01T20:30:00Z
updated: 2026-05-01T22:15:00Z
status: gaps_found
score: 4/4 ROADMAP-SCs verified; CR-01 resolved post-verification; user-driven Clean Code sweep added as additional gaps
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 4/4 ROADMAP-SCs verified, 1 BLOCKER (CR-01)
  gaps_closed:
    - "CR-01 — sortIndex cross-phase collision (fixed in commit 98bcfe2 + 2 regression tests; documented in 61-REVIEW-FIX.md)"
    - "WR-01 through WR-07 — code review warnings (all 7 fixed; documented in 61-REVIEW-FIX.md)"
  gaps_remaining: []
  regressions: []
gaps:
  - truth: "Codebase is free of dead references (Phase Goal verbatim) — CR-01 cross-phase finder regression"
    status: resolved
    resolution: |
      Fixed in commit 98bcfe2 via /gsd-code-review-fix workflow (2026-05-01).
      MatchdayService.createInline + CsvImportService.findOrCreateMatchday + checkDuplicate
      now use findByPhaseIdOrderBySortIndexAsc(regular.getId()) — phase-scoped finder.
      2 regression tests added. Final test gate: 1172 Surefire tests, BUILD SUCCESS.
      See 61-REVIEW-FIX.md for full per-finding remediation report.
    artifacts:
      - path: "src/main/java/org/ctc/domain/service/MatchdayService.java"
        issue: "RESOLVED — line 167 now phase-scoped"
      - path: "src/main/java/org/ctc/dataimport/CsvImportService.java"
        issue: "RESOLVED — lines 445 + 451 now phase-scoped"
  - truth: "Codebase is free of stale comments (Clean Code sweep — user-driven scope addition 2026-05-01)"
    status: failed
    reason: |
      Phase 61 cascade migration (Wave 2) added narrative comments referencing
      transient state ("Wave 2 cascade migration", "transitional bridge field",
      "used by X", "added for Y flow") that violate CLAUDE.md guidance: "Don't
      explain WHAT the code does ... don't reference the current task, fix, or
      callers — those belong in the PR description and rot as the codebase
      evolves." User explicitly requested codebase-wide sweep on 2026-05-01.
    scope: "Komplette Codebase (src/main + src/test) — nicht nur Phase-61-touched files"
    missing:
      - "Identify all stale comments via grep + manual review across src/main/**/*.java + src/test/**/*.java"
      - "Remove comments that describe WHAT (not WHY)"
      - "Remove comments referencing concrete tasks/PRs/incidents (e.g. 'cascade migration', 'D-XX', 'bridge field')"
      - "Remove obsolete @deprecated tags whose target was already removed"
      - "Verify removal does not break javadoc-dependent tooling (IDE, linters, doc generation)"
  - truth: "Codebase is free of dead code branches (Clean Code sweep — user-driven scope addition 2026-05-01)"
    status: failed
    reason: |
      Phase 61 review (61-REVIEW.md) found WR-02 (dead getDetailData), WR-01
      (dead alltime-standings fallback), and deferred-items.md tracks
      PlayoffService.playoffSeedRepository unused field. These are likely
      symptoms of a broader pattern: cascade migration left dead branches,
      ungenutzte private methods, dead imports, and unused fields. CLAUDE.md
      rule: "Don't add error handling, fallbacks, or validation for scenarios
      that can't happen."
    scope: "Komplette Codebase — verify each potentially-dead branch via grep + coverage data"
    missing:
      - "Grep all @Deprecated annotations and verify removal targets"
      - "Identify unreachable if/else branches via static analysis (IntelliJ inspections or similar)"
      - "Identify unused private methods + fields via grep + coverage map"
      - "Identify dead imports across all src files"
      - "Remove each only after confirming via test coverage that no uncovered code path needs it"
      - "Maintain 82% line coverage threshold (D-21 — never lower)"
  - truth: "Codebase has appropriate validation boundaries (Clean Code sweep — user-driven scope addition 2026-05-01)"
    status: failed
    reason: |
      CLAUDE.md hard rule: "Don't add error handling, fallbacks, or validation
      for scenarios that can't happen. Trust internal code and framework
      guarantees. Only validate at system boundaries (user input, external
      APIs)." Cascade migration likely added defensive Objects.requireNonNull
      and similar at internal-API boundaries where the framework already
      guarantees non-null contracts.
    scope: "Komplette Codebase — focus on services + repositories (not controllers, where Mass Assignment validation IS appropriate)"
    missing:
      - "Identify Objects.requireNonNull calls at internal-API boundaries (between services, between service+repository)"
      - "Identify defensive null guards on @Autowired fields (Spring guarantees non-null)"
      - "Identify try/catch blocks that wrap impossible exceptions"
      - "Distinguish: controllers + system boundaries (KEEP validation) vs internal services (REMOVE redundant validation)"
      - "Each removal MUST have a regression test confirming the removed validation was unreachable"
  - truth: "Public APIs have accurate javadoc (Clean Code sweep — user-driven scope addition 2026-05-01)"
    status: failed
    reason: |
      Phase 61 review found IN-03 (SeasonRepository finders lack post-V6
      javadoc explaining new phase indirection), IN-05 (StandingsService
      contradicting javadoc). After cascade migration, javadoc on services
      and repositories likely drifted from current behavior — common
      symptom: javadoc says "returns regular matchdays" but method now
      returns all-phase matchdays.
    scope: "Komplette Codebase public APIs — domain.service, domain.repository, admin.service, admin.controller"
    missing:
      - "Audit javadoc on every public method in domain.service + domain.repository"
      - "Fix outdated javadoc that contradicts current behavior"
      - "Add javadoc to public methods that lack it (esp. those with non-obvious semantics post-cascade)"
      - "Reference: SeasonRepository finders, MatchdayRepository finders, StandingsService.calculateBuchholzScoresForPhase, anything touching phase-vs-season boundary"
      - "Do NOT add docstring boilerplate — only add javadoc when WHY is non-obvious (CLAUDE.md guidance)"
deferred: []
human_verification:
  - test: "GROUPS-Saison E2E flow visual smoke check"
    expected: "Manually create a GROUPS-layout season with 2 groups via /admin/seasons/new, assign teams, generate matchdays per group; verify per-group standings + combined-view rendering matches expectations from QUAL-02 acceptance criteria"
    why_human: "QUAL-02 E2E test deviates from D-15 mandate by performing matchday/race/lineup setup via repositories (no UI affordance for group-bound matchday generation as of Phase 60). Plan 61-04 SUMMARY documents this Rule-3 deviation. Visual confirmation that the read-only standings rendering for GROUPS layout matches user expectations is a manual sanity check."
  - test: "Legacy migrated season visual smoke check"
    expected: "Open an actual pre-v1.9 season (one that was migrated by V4) in the running app and verify exactly 1 REGULAR phase tab + all matchdays accessible + race detail loads + standings render — both with and without playoff"
    why_human: "QUAL-03 fixtures seed 0 race-results (read-only path per D-18) — the Standings empty-state path is exercised but NOT the populated standings table. Real production data will hit the populated path; manual smoke ensures no rendering regression with real data."
  - test: "V6 migration on MariaDB"
    expected: "Boot the docker-compose stack (./mvnw spring-boot:run with profile=docker, MariaDB) and verify Flyway applies V6 cleanly without 'Cannot drop column referenced by FK' or 'Index references missing column' errors"
    why_human: "V6MigrationTest only exercises H2 (dev profile). The defensive DROP CONSTRAINT IF EXISTS / DROP INDEX IF EXISTS guards are MariaDB-specific safeguards that H2 does not require. Production deploy MUST be smoke-tested on MariaDB before PR merge per Plan 61-03 D-23 (IRREVERSIBLE schema change)."
  - test: "Legacy URL bookmark regression"
    expected: "Verify previously-shared admin URLs continue to work: /admin/standings?seasonId=<oldId> resolves to REGULAR phase view; /admin/playoffs/{id}/add-season returns the global error page (Phase 61 D-03 Tracked Behavior Change)"
    why_human: "Plan 61-02 D-03 explicitly tracks this URL behavior change. Confirmation that the runtime maps old POST routes to a 5xx error page (not 404) is a manual UAT step before release."
---

# Phase 61: Cleanup & Quality Gate Verification Report

**Phase Goal:** "The old bridge columns and join table are removed from the schema, the codebase is free of dead references, JaCoCo line coverage is at or above 82%, and the full E2E test suite validates a complete GROUPS season workflow and confirms that migrated legacy seasons remain accessible."

**Verified:** 2026-05-01T20:30:00Z
**Status:** gaps_found (1 BLOCKER from code review)
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| #   | Truth (from ROADMAP SC + Phase Goal)                                                                                                                                                                            | Status     | Evidence                                                                                                                                                                                                                                                                              |
| --- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | SC1: Flyway cleanup migration drops 8 seasons cols + 2 bridge FK cols + `playoff_seasons` join table; `./mvnw verify` green                                                                                     | VERIFIED   | V6 SQL contains 1 `DROP TABLE playoff_seasons`, 2 `ALTER TABLE matchdays/playoffs DROP COLUMN season_id`, 8 `ALTER TABLE seasons DROP COLUMN ...` (verified via grep). Surefire 1171/0/0/1, Failsafe 31, BUILD SUCCESS. V6MigrationTest with 4 INFORMATION_SCHEMA assertions all green. |
| 2   | SC2: `./mvnw verify` reports JaCoCo line coverage >= 82%                                                                                                                                                        | VERIFIED   | Line coverage 87.05% (5634/6472) — 5.05 pp headroom above the 0.82 threshold (`pom.xml` `<minimum>0.82</minimum>` confirmed unchanged). CSV recomputation confirms instruction 84.41%, branch 74.32%, line 87.05%.                                                                    |
| 3   | SC3: GROUPS E2E test creates 2-group season, imports drivers via mocked sheet (group resolved via PhaseTeam), generates matchdays per group, records results, verifies per-group + combined standings           | VERIFIED   | `GroupsSeasonE2ETest.givenGroupsLayout_whenFullSeasonWorkflow_thenStandingsCorrect` (521 lines) — STEP 1-9 cover the full path; `@TestConfiguration TestGoogleSheetsConfig` returns 12 driver rows; PhaseTeam-resolution exercised via T-GA-*/T-GB-* shortNames; D-13 hybrid asserts.  |
| 4   | SC4: Regression Playwright test opens migrated legacy season, confirms exactly 1 REGULAR-tab + all matchdays + races accessible (+ PLAYOFF tab if playoff migrated)                                             | VERIFIED   | `LegacyMigratedSeasonE2ETest` with 2 @Sql-driven @Test methods covering both ROADMAP-SC4 sub-cases. Fixtures `legacy-season-without-playoff.sql` + `legacy-season-with-playoff.sql` use post-V6 schema, deterministic UUIDs, T-prefix isolation.                                      |
| G1  | Phase Goal sub-clause: "the codebase is free of dead references"                                                                                                                                                | **FAILED** | CR-01 (61-REVIEW.md, BLOCKER): cross-phase finder usage in `MatchdayService.createInline:167` + `CsvImportService.findOrCreateMatchday:445,451` introduces a sortIndex collision and a duplicate-label collision when seasons have both REGULAR + PLAYOFF matchdays.                  |

**Score:** 4/4 ROADMAP Success Criteria verified, but the broader Phase Goal sub-clause "codebase is free of dead references" fails per CR-01 (logic regression, not strictly "dead reference" — see Gaps Summary for the interpretation discussion).

### Required Artifacts

| Artifact                                                                       | Expected                                                                          | Status     | Details                                                                                                                                                                                       |
| ------------------------------------------------------------------------------ | --------------------------------------------------------------------------------- | ---------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `src/main/resources/db/migration/V6__cleanup_legacy_season_columns.sql`        | 1 DROP TABLE + 2 bridge DROP COLUMN + 8 seasons DROP COLUMN, FK-safe order        | VERIFIED   | 39 lines; defensive `DROP CONSTRAINT IF EXISTS` + `DROP INDEX IF EXISTS` guards added beyond plan spec. `IRREVERSIBLE` marker present.                                                        |
| `src/test/java/db/migration/V6MigrationTest.java`                              | Surefire `*Test.java` suffix, 4 INFORMATION_SCHEMA assertions                     | VERIFIED   | 90 lines; @SpringBootTest @ActiveProfiles("dev"); 4 @Test methods present including `JpaMappingStillWorks` for ddl-auto=validate.                                                              |
| `src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java`                           | extends PlaywrightConfig, 1 @Test method, hybrid UI + DB-state asserts            | VERIFIED   | 521 lines; @Import(TestGoogleSheetsConfig); 14 @Autowired repositories+services; 1 @Test (`givenGroupsLayout_whenFullSeasonWorkflow_thenStandingsCorrect`).                                    |
| `src/test/java/org/ctc/e2e/LegacyMigratedSeasonE2ETest.java`                   | extends PlaywrightConfig, 2 @Sql-driven @Test methods                             | VERIFIED   | 145 lines; both @Test methods present with @Sql BEFORE_TEST_METHOD; deterministic UUID constants match fixture inserts.                                                                       |
| `src/test/resources/sql/legacy-season-without-playoff.sql`                     | Post-V6 schema INSERT shape (no format/legs/season_id columns), deterministic UUIDs | VERIFIED   | 91 lines; 1 INSERT INTO seasons (slim cols), 1 INSERT INTO season_phases (REGULAR), 0 INSERT INTO playoffs.                                                                                  |
| `src/test/resources/sql/legacy-season-with-playoff.sql`                        | Same structure + PLAYOFF phase + 1 playoff row                                    | VERIFIED   | 104 lines; 2 INSERT INTO season_phases (REGULAR + PLAYOFF), 1 INSERT INTO playoffs (no `season_id` column).                                                                                  |
| Slim entity: `Season.java` (no format/legs/totalRounds/eventDuration/dates/raceScoring/matchScoring) | All 8 legacy fields removed                                                       | VERIFIED   | grep returns no matches for any of the 8 dropped field names in Season.java.                                                                                                                  |
| Slim entity: `Matchday.java` / `Playoff.java` — no `seasonId` bridge field, no `@PrePersist` | Bridge removal complete post-V6                                                   | VERIFIED   | Only matches are javadoc references documenting the historical column drop (D-06 transitional removed in 61-03).                                                                              |
| `pom.xml` JaCoCo threshold preserved at 0.82                                   | `<minimum>0.82</minimum>` unchanged                                               | VERIFIED   | grep count = 1 hit; matches D-21.                                                                                                                                                             |
| `application-{dev,local,docker,prod}.yml` ddl-auto: validate                   | All 4 profiles agree                                                              | VERIFIED   | 4 grep hits, one per profile; schema-vs-entity match guaranteed in CI for dev profile.                                                                                                        |

### Key Link Verification

| From                                                | To                                                              | Via                                          | Status     | Details                                                                                                                                                |
| --------------------------------------------------- | --------------------------------------------------------------- | -------------------------------------------- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| V6 SQL                                              | post-V6 schema matches trimmed entities                         | Hibernate `ddl-auto: validate`               | VERIFIED   | V6MigrationTest.givenV6HasRun_whenLoadAllSeasons_thenJpaMappingStillWorks calls seasonRepository.findAll() → no Hibernate validate failure.            |
| V6MigrationTest                                     | actual V6 schema state                                          | INFORMATION_SCHEMA assertions                | VERIFIED   | 4 @Test methods cover seasons cols, playoff_seasons table, matchdays/playoffs.season_id bridge cols, JPA mapping.                                       |
| GroupsSeasonE2ETest                                 | PhaseTeam group resolution during driver import                 | TestGoogleSheetsConfig stub returns 12 rows  | VERIFIED   | Stub does NOT include a Group column; group is resolved via T-GA-*/T-GB-* short_name → PhaseTeam.group lookup (Phase 59 D-05 path exercised).         |
| LegacyMigratedSeasonE2ETest                         | post-V6 schema fixture insert                                   | @Sql BEFORE_TEST_METHOD                      | VERIFIED   | Fixtures omit dropped columns; INSERT INTO seasons uses slim shape; @SpringBootTest startup runs Flyway V1→V6 then @Sql executes pre-insert.            |
| MatchdayService.createInline                        | next REGULAR matchday sortIndex                                 | findBySeasonIdOrderBySortIndexAsc(seasonId)  | **NOT_WIRED** | CR-01: finder returns matchdays across ALL phases including PLAYOFF. sortIndex calculation includes playoff matchdays' 100+ values. Should use phase-scoped finder. |
| CsvImportService.findOrCreateMatchday               | matchday lookup by label + sortIndex compute                    | findBySeasonIdOrderBySortIndexAsc(seasonId)  | **NOT_WIRED** | CR-01 same root cause; lines 445 + 451.                                                                                                                |

### Data-Flow Trace (Level 4)

| Artifact                                          | Data Variable                          | Source                                              | Produces Real Data | Status                |
| ------------------------------------------------- | -------------------------------------- | --------------------------------------------------- | ------------------ | --------------------- |
| GroupsSeasonE2ETest                               | UI render of standings + DB asserts    | Real H2 DB persisted via UI clicks + repo writes    | Yes                | FLOWING               |
| LegacyMigratedSeasonE2ETest                       | UI render of season-detail/standings   | @Sql-injected fixture rows in H2                    | Yes (read-only)    | FLOWING (read-only)   |
| V6MigrationTest                                   | INFORMATION_SCHEMA query results       | Live H2 schema after Flyway V1→V6                   | Yes                | FLOWING               |

### Behavioral Spot-Checks

| Behavior                                          | Command                                                            | Result                                          | Status     |
| ------------------------------------------------- | ------------------------------------------------------------------ | ----------------------------------------------- | ---------- |
| Full verify gate green                            | `./mvnw verify -Pe2e` (executed in Wave 5)                         | BUILD SUCCESS                                   | PASS       |
| V6 migration syntax valid (H2)                    | `@SpringBootTest` startup runs Flyway V1→V6                        | V6MigrationTest@SpringBootTest starts cleanly   | PASS       |
| JaCoCo coverage threshold met                     | recompute from `target/site/jacoco/jacoco.csv`                     | 87.05% line coverage (5634/6472)                | PASS       |
| `pom.xml` threshold unchanged                     | `grep -c '<minimum>0.82</minimum>' pom.xml`                        | 1                                               | PASS       |
| All Flyway migrations present                     | `ls src/main/resources/db/migration/V*.sql`                        | V1, V2, V3, V5, V6 present (V4 in Java)         | PASS       |
| V6 migration on MariaDB (docker profile)          | (cannot run in this environment without docker stack)              | -                                               | SKIP (human) |
| createInline sortIndex post-playoff regression    | (no test exists; CR-01 finding is by code analysis)                | -                                               | SKIP (gap) |

### Requirements Coverage

| Requirement | Source Plan | Description (REQUIREMENTS.md) | Status | Evidence |
| ----------- | ---------- | ------------------------------ | ------ | -------- |
| MIGR-06     | 61-01, 61-02, 61-03 | Cleanup-Migration: alte Spalten aus `seasons` + M:N `playoff_seasons` entfernt (Phase 61 in REQUIREMENTS.md traceability) | SATISFIED | V6 drops 8 seasons cols + bridge cols + M:N table; entities slimmed; V6MigrationTest green; 1171 Surefire / 31 Failsafe tests pass. |
| QUAL-01     | 61-05       | JaCoCo Line-Coverage ≥ 82 % gehalten | SATISFIED | Coverage 87.05% (5.05 pp headroom); pom.xml unchanged at 0.82 threshold. |
| QUAL-02     | 61-04       | E2E-Test deckt GROUPS-Saison: Anlegen, Roster pro Group, Matchdays pro Group, Driver-Import mit Group-Auflösung, Standings pro Group + Combined | SATISFIED (with note) | GroupsSeasonE2ETest passes; covers all 6 sub-aspects. NOTE: matchday/race generation done via repositories (no UI affordance) — D-15 mandate honoured strictly for race-results UI entry. Plan 61-04 SUMMARY documents this Rule-3 deviation. |
| QUAL-03     | 61-05       | Regression-Test: Bestandssaison öffnet nach Migration mit 1 REGULAR-Phase + allen Race-Daten erreichbar | SATISFIED (with note) | LegacyMigratedSeasonE2ETest passes (2 variants: with/without playoff); read-only fixtures with 0 race-results — empty-state standings path exercised but populated-standings path NOT covered. Documented in Plan 61-05 SUMMARY as intentional (D-18 read-only). |

**Orphaned requirements:** None — REQUIREMENTS.md traceability table maps exactly MIGR-06 + QUAL-01 + QUAL-02 + QUAL-03 to Phase 61, and all are claimed by at least one plan's `requirements:` field.

### Anti-Patterns Found

| File                                                                       | Line     | Pattern                          | Severity   | Impact                                                                                                       |
| -------------------------------------------------------------------------- | -------- | -------------------------------- | ---------- | ------------------------------------------------------------------------------------------------------------ |
| `src/main/java/org/ctc/domain/service/MatchdayService.java`                | 167      | Cross-phase finder for sortIndex | BLOCKER    | CR-01 (61-REVIEW.md): sortIndex jumps to 100+ when playoff matchdays exist; breaks `@OrderBy` invariant.     |
| `src/main/java/org/ctc/dataimport/CsvImportService.java`                   | 445, 451 | Same cross-phase finder pattern  | BLOCKER    | CR-01: same root cause; CSV import duplicates the bug.                                                       |
| `src/main/java/org/ctc/sitegen/SiteGeneratorService.java`                  | 590-597  | Dead fallback path that would crash if reached | WARNING    | WR-01 (61-REVIEW.md): `@SuppressWarnings("deprecation")` is stale; the fallback would throw EntityNotFoundException post-V6. |
| `src/main/java/org/ctc/domain/service/SeasonManagementService.java`        | 139-148  | Dead `getDetailData` method      | WARNING    | WR-02: 0 callers; would throw on legacy seasons missing REGULAR phase.                                       |
| `src/main/resources/db/migration/V6__cleanup_legacy_season_columns.sql`    | 9        | `DROP TABLE` without `IF EXISTS` | INFO       | WR-03: cosmetic inconsistency; not a correctness bug (Flyway runs once).                                     |
| `src/main/java/org/ctc/domain/service/RaceCalendarService.java`            | 67-76    | Missing null guard on getMatchday().getPhase() | WARNING    | WR-06: NPE possible for corrupt playoff race; current fixtures don't trigger it.                             |
| `src/main/java/org/ctc/admin/TestDataService.java`                         | 957-985  | Hand-rolled standings vs `playoffSeedingService.autoSeedBracket` | WARNING    | WR-07: dev-mode demo data only; inconsistent algorithm with the canonical 2023 path.                         |
| `src/main/java/org/ctc/domain/service/StandingsService.java`               | 240-245  | Unused `groupId` parameter on `calculateBuchholzScoresForPhase` | INFO       | IN-05: stale-after-refactor smell; non-blocking.                                                              |
| Other 61-REVIEW.md WARN/INFO items (WR-04, WR-05, IN-01, IN-02, IN-03, IN-04) | various  | various                          | various    | See `.planning/phases/61-cleanup-quality-gate/61-REVIEW.md` for full classification.                          |

### Human Verification Required

See frontmatter `human_verification:` for the 4 items.

1. **GROUPS-Saison E2E flow visual smoke check** — QUAL-02 test does matchday/race setup via repositories (no group-bound UI exists per Phase 60); manual GROUPS layout sanity needed.
2. **Legacy migrated season visual smoke check** — Fixtures seed 0 race-results; populated standings rendering not exercised by automated tests.
3. **V6 migration on MariaDB (docker profile)** — V6MigrationTest only exercises H2; defensive `IF EXISTS` guards are MariaDB-specific. Mandatory before PR per Plan 61-03 D-23 (IRREVERSIBLE schema change in prod).
4. **Legacy URL bookmark regression** — D-03 Tracked Behavior Change: `/admin/playoffs/{id}/add-season` returns 5xx (not 404); needs UAT confirmation.

### Gaps Summary

**Phase Status: gaps_found.**

The 4 ROADMAP Success Criteria (SC1, SC2, SC3, SC4) are all VERIFIED with strong codebase evidence — V6 migration is correctly authored and tested, JaCoCo line coverage is 87.05% (5.05 pp above the 82% threshold), the GROUPS E2E covers the SC3 path end-to-end, and the legacy regression covers SC4.

However, the broader Phase Goal sentence includes the sub-clause **"the codebase is free of dead references"**, and the Wave 5 code review (`61-REVIEW.md`) classified **CR-01 as a BLOCKER**:

`MatchdayRepository.findBySeasonIdOrderBySortIndexAsc` is now phase-spanning (`WHERE m.phase.season.id = :seasonId`). Two production callsites — `MatchdayService.createInline:167` and `CsvImportService.findOrCreateMatchday:445,451` — assume the finder returns REGULAR-phase matchdays only and use it to compute the next sortIndex and to enforce duplicate-label uniqueness. Once a season has a PLAYOFF phase with `addRaceToMatchup`-created matchdays (which use `sortIndex = 100 + roundIdx*10 + legNum`), the next REGULAR matchday created via either callsite will jump to `sortIndex >= 101`, breaking the `@OrderBy("sortIndex ASC")` invariant on `SeasonPhase.matchdays`, and a REGULAR "Round 1" create will fail when a PLAYOFF "Round 1" already exists.

**Why no test caught this:** No test seeds a season with both REGULAR + PLAYOFF matchdays AND then exercises `createInline`/`findOrCreateMatchday`. The existing 1171 Surefire + 31 Failsafe tests build seasons from-scratch, in single-phase contexts. The bug is in the cross-cutting interaction between Plan 61-02's cascade migration (which redefined the finder's semantics) and `PlayoffService.addRaceToMatchup`'s sortIndex convention (pre-existing).

**Why CR-01 cannot be deferred:** Phase 61 is the LAST phase of milestone v1.9 (per ROADMAP.md). There is no Phase 62 to absorb this. The phase goal explicitly demands "the codebase is free of dead references" — CR-01 is technically a logic regression rather than a strict "dead reference", but it was introduced by Phase 61's cleanup cascade and surfaces a pre-existing semantic ambiguity (phase-spanning vs phase-scoped finders) that Phase 61 has the responsibility to resolve before declaring the cleanup complete.

**Recommended fix scope (small, ~30 min):**
1. `MatchdayService.createInline:167`: replace with `matchdayRepository.findByPhaseIdOrderBySortIndexAsc(regular.getId())` (the `regular` variable already exists at line 165).
2. `CsvImportService.findOrCreateMatchday:445,451`: same replacement (resolve `regular` once at line 444, reuse twice).
3. Add a regression test in `MatchdayServiceTest` that creates a season with a playoff (via `TestDataService.seedPlayoffs` analog or `playoffService.createPlayoff`) then calls `createInline` and asserts `sortIndex == lastRegularSortIndex + 1` (NOT >100).
4. Add a similar regression test in `CsvImportServiceTest` for `findOrCreateMatchday`.

**WR-01 through WR-07 (warnings):** Not blocking phase exit on their own, but several (WR-04, WR-06) describe latent bugs that would be lower risk to address in the same closure plan as CR-01 since the affected files (RaceCalendarService, SiteGeneratorService) overlap.

**Human verification (4 items):** Even after CR-01 is fixed, the phase status would move to `human_needed` — not `passed` — because the QUAL-02/QUAL-03 deviations (no group-bound UI, 0 race-results in legacy fixtures) and the MariaDB-untested V6 migration require manual UAT before the v1.9 milestone can ship.

---

## Re-Verification Update — 2026-05-01T22:15:00Z (User-Driven Scope Addition)

### CR-01 + WR-01..07 Resolved

The original BLOCKER (CR-01) and all 7 WARNINGs (WR-01 through WR-07) from `61-REVIEW.md` were resolved post-verification via the `/gsd-code-review-fix 61` workflow. See `61-REVIEW-FIX.md` for full per-finding remediation report. Final test gate: 1172 Surefire tests, BUILD SUCCESS. The original `gaps_found` reason for CR-01 is now closed.

### Additional Gaps — Clean Code Sweep (User Request)

The user (Jens, 2026-05-01) flagged that Phase 61's Goal sub-clause *"codebase is free of dead references"* is interpreted too narrowly if it only addresses functional dead references (à la CR-01). A clean cleanup must also address Clean Code hygiene at the comment/branch/validation/javadoc level, codebase-wide.

The user explicitly chose:

- **Scope:** Komplette Codebase (entire `src/main` + `src/test`), not only Phase-61-touched files
- **Issue classes (4 selected):**
  1. **Stale comments** — comments describing WHAT (not WHY), referencing concrete tasks/PRs/cascade-migration narrative, obsolete @deprecated tags
  2. **Dead code branches** — unreachable if/else, unused private methods/fields, dead imports
  3. **Defensive over-validation** — `Objects.requireNonNull` at internal-API boundaries where the framework already guarantees non-null (CLAUDE.md: "only validate at system boundaries")
  4. **Missing/wrong javadoc** — public APIs without javadoc, or javadoc that contradicts current post-cascade behavior

### Why Gap-Closure (not new Phase 62)

Phase 61's Goal explicitly contains the sub-clause "codebase is free of dead references" — Clean Code hygiene is in-scope. Routing this through Phase 61 gap-closure (`/gsd-plan-phase 61 --gaps` → `/gsd-execute-phase 61 --gaps-only` → re-verify) keeps milestone v1.9's plan intact. A new Phase 62 would dilute the milestone boundary and is not warranted.

### Constraints for Gap Plans

- **Test gate preserved:** Each removal MUST keep `./mvnw verify -Pe2e` BUILD SUCCESS and JaCoCo line coverage ≥ 82% (D-21 — never lower threshold).
- **Atomic commits:** One logical removal per commit (e.g., "refactor(61): remove stale 'Wave 2 cascade' comments from domain.service") so any regression can be bisected.
- **Wave-aligned:** Plans should split by file-group (e.g., domain.model, domain.service, domain.repository, admin.controller, dataimport, sitegen, admin.service, test) so subagent work fits within token budget — Wave 2 hit a 2.6h timeout at 277 tool calls; aim for <60 tool calls per plan.
- **Defensive removal:** Each "dead code" removal MUST verify via grep + coverage that no uncovered path reaches it. False-positive dead-code removal regressions are unacceptable.
- **Cascade aware:** Phase 61 itself recently touched many of these files. The planner MUST consult `61-REVIEW.md` + `61-REVIEW-FIX.md` to avoid re-introducing fixed issues or removing recently-added intentional code.

### Original Gaps (Snapshot @ 2026-05-01T20:30:00Z) Below

The `## Goal Achievement` and `## Gaps Summary` sections above are preserved as the original verification snapshot. The `frontmatter.gaps[]` list reflects the current state (CR-01 resolved + 4 new clean-code gaps).

---

_Verified: 2026-05-01T20:30:00Z_
_Re-verified scope addition: 2026-05-01T22:15:00Z (user-driven Clean Code sweep)_
_Verifier: Claude (gsd-verifier)_
