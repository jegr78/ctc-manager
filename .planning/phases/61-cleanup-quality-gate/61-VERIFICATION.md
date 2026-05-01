---
phase: 61-cleanup-quality-gate
verified: 2026-05-01T20:30:00Z
status: gaps_found
score: 4/4 ROADMAP-SCs verified, but 1 BLOCKER from code review affects production correctness
overrides_applied: 0
re_verification:
  previous_status: null
  previous_score: null
  gaps_closed: []
  gaps_remaining: []
  regressions: []
gaps:
  - truth: "Codebase is free of dead references (Phase Goal verbatim)"
    status: failed
    reason: |
      CR-01 (BLOCKER from 61-REVIEW.md): MatchdayService.createInline (line 167) and
      CsvImportService.findOrCreateMatchday (lines 445, 451) call
      MatchdayRepository.findBySeasonIdOrderBySortIndexAsc which now returns matchdays from
      ALL phases (REGULAR + PLAYOFF). For seasons with playoffs:
      (a) sortIndex of next REGULAR matchday gets poisoned by playoff matchday's sortIndex
          (PlayoffService writes 100+roundIdx*10+legNum, so REGULAR jumps to >=101).
      (b) duplicate-label check now collides across phases (REGULAR "Round 1" rejected when
          PLAYOFF "Round 1" already exists).
      This is a logic regression introduced by Wave 2 cascade migration. Surefire gate did
      NOT catch it because no test creates a season with both REGULAR + PLAYOFF matchdays
      then exercises createInline/findOrCreateMatchday afterwards.
      Phase 61 is the LAST phase of milestone v1.9 — no later phase can absorb this.
    artifacts:
      - path: "src/main/java/org/ctc/domain/service/MatchdayService.java"
        issue: "Line 167 uses cross-phase finder; sortIndex calculation includes playoff matchdays"
      - path: "src/main/java/org/ctc/dataimport/CsvImportService.java"
        issue: "Lines 445 + 451 use the same cross-phase finder for findOrCreateMatchday"
    missing:
      - "Replace findBySeasonIdOrderBySortIndexAsc(season.getId()) with findByPhaseIdOrderBySortIndexAsc(regular.getId()) in MatchdayService.createInline"
      - "Replace both findBySeasonIdOrderBySortIndexAsc(season.getId()) calls in CsvImportService.findOrCreateMatchday with phase-scoped finder"
      - "Add regression test: create season with playoff seeded, call createInline, assert new matchday sortIndex == lastRegularSortIndex+1 (NOT >100)"
      - "Add regression test: REGULAR Round 1 + PLAYOFF Round 1 do not collide on duplicate-label check"
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

_Verified: 2026-05-01T20:30:00Z_
_Verifier: Claude (gsd-verifier)_
