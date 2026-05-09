---
phase: 56-model-schema-foundation
verified: 2026-04-26T18:00:00Z
status: passed
score: 5/5 success criteria verified (revised reading per D-01)
overrides_applied: 1
overrides:
  - must_have: "Success Criterion #3 — Season entity no longer carries format/scoring/dates/totalRounds/legs fields"
    reason: "Reinterpreted in 56-CONTEXT.md D-01 (parallel-additive strategy). The literal removal of these fields is deferred to Phase 61 (MIGR-06). Phase 56 delivers only the additive part: Matchday.phase_id and Playoff.phase_id FKs are present. The reinterpretation is documented in 56-CONTEXT.md and 56-DISCUSSION-LOG.md."
    accepted_by: "User during /gsd-discuss session 2026-04-26"
    accepted_at: "2026-04-26T00:00:00Z"
re_verification:
  previous_status: none
  previous_score: n/a
  gaps_closed: []
  gaps_remaining: []
  regressions: []
human_verification: []
---

# Phase 56 Verification — Model & Schema Foundation

## Verdict
**PASS** (with one documented override on Success Criterion #3 — see Override note below)

## Phase Goal
> "The new three-level hierarchy (Season -> Phase -> Group) exists as compilable JPA entities backed by Flyway-created tables, with all FK relationships in place and old entities updated to reference the new FK columns."
>
> — `.planning/ROADMAP.md` Phase 56

## Success Criteria Audit

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | Application starts and all tests pass after adding the three new entity classes and their repositories | PASS | `target/surefire-reports/*.txt` aggregated: 695 Surefire tests, 0 failures, 0 errors, 0 skipped (re-counted from disk). New tests `SeasonPhaseEntityIntegrationTest` (6/0/0) and `PhaseTeamUniquenessIntegrationTest` (2/0/0) both green. SUMMARY claims 1072 tests (Surefire+Failsafe combined per 56-05-SUMMARY); local Surefire alone shows 695 — both consistent with `./mvnw verify` exit 0. |
| 2 | Flyway executes cleanly on H2 + MariaDB; creates `season_phases`, `season_phase_groups`, `phase_teams` with correct columns and constraints | PASS | `src/main/resources/db/migration/V3__add_season_phase_tables.sql` lines 5-49 contain the three CREATE TABLE blocks with all required columns, FK constraints (10), and UNIQUE keys (3). Flyway runs as part of every Spring Boot test; the 6 SeasonPhaseEntityIntegrationTest methods all exercise the V3-created tables on H2 dev profile and pass. ANSI-portable SQL (no DB-specific syntax). |
| 3 | Season entity no longer carries format/scoring/dates/totalRounds/legs fields; Matchday carries phase_id FK; Playoff carries phase_id FK | **PASS (override)** | **Reinterpreted per D-01 / 56-CONTEXT.md**: literal removal of Season fields is Phase 61's job (MIGR-06). Phase 56 delivers ONLY the additive part. Verified in code: `Matchday.java:30-32` carries `private SeasonPhase phase` with `@JoinColumn(name = "phase_id")`; `Playoff.java:33-35` carries `private SeasonPhase phase` with `@JoinColumn(name = "phase_id")`. Season legacy fields remain at `Season.java:47, 49, 52, 55, 59, 63` per D-01 explicit instruction. |
| 4 | UNIQUE constraint preventing more than one REGULAR phase per season is enforced at the database level | PASS | `V3__add_season_phase_tables.sql:25` declares `CONSTRAINT uk_season_phase_type UNIQUE (season_id, phase_type)`. Runtime enforcement proven by `PhaseTeamUniquenessIntegrationTest.givenSeasonWithRegularPhase_whenSecondRegularPhaseInserted_thenViolatesUniqueSeasonPhaseType` (line 68-86) — passes (`Tests run: 2, Failures: 0, Errors: 0`). |
| 5 | No existing Flyway V1 or V2 migration file is modified (checksum integrity preserved) | PASS | `git diff origin/master...HEAD -- src/main/resources/db/migration/V1__initial_schema.sql src/main/resources/db/migration/V2__add_fk_indexes.sql` returned NO output — both files byte-identical to origin/master. Only V3 is new (`f9c29f2`). |

**Score:** 5/5 PASS (1 with documented override on SC#3 per D-01).

## Decision Compliance (D-01..D-08)

| Decision | Status | Evidence |
|----------|--------|----------|
| **D-01** — Parallel additive on Season (legacy fields retained), Matchday (existing season FK retained), Playoff (existing season + seasons M:N + playoff_seasons retained) | PASS | `Season.java:47` (`SeasonFormat format`), `:49` (`Integer totalRounds`), `:52` (`int legs`), `:55` (`Integer eventDurationMinutes`), `:38-40` (`startDate`/`endDate`), `:59` (`raceScoring`), `:63` (`matchScoring`), `:67` (`matchdays`) all present. `Matchday.java:26-28` keeps `season` FK with `nullable = false`. `Playoff.java:28-31` keeps `@NotNull season` with `unique = true`; `:53` keeps `seasons` M:N; `:49-52` keeps `playoff_seasons` join table. New `Season.phases` at `:69-71`. New `Matchday.phase` (`:30-32`), `Matchday.group` (`:34-36`). New `Playoff.phase` (`:33-35`). |
| **D-02** — `matchdays.phase_id`, `matchdays.group_id`, `playoffs.phase_id` NULLABLE in V3 (NOT NULL flip is Phase 57); `playoffs.phase_id` is UNIQUE; ON DELETE RESTRICT (no CASCADE) on all V3 FKs | PASS | `V3__add_season_phase_tables.sql:51-58`: `ALTER TABLE matchdays ADD COLUMN phase_id UUID;` (no NOT NULL), `ADD COLUMN group_id UUID;` (no NOT NULL), `playoffs ADD COLUMN phase_id UUID;` (no NOT NULL). Line 58: `ADD CONSTRAINT uk_playoff_phase UNIQUE (phase_id)`. Grep confirms zero `ON DELETE CASCADE` and zero `CHECK` in V3. |
| **D-03** — `uk_season_phase_type (season_id, phase_type)` and `uk_phase_team (phase_id, team_id)` UNIQUE constraints; integration test proves `uk_phase_team` rejects duplicates | PASS | `V3:25` (`uk_season_phase_type`) and `V3:48` (`uk_phase_team`). Runtime proof in `PhaseTeamUniquenessIntegrationTest.java:88-110` — second test inserts a duplicate `(phase_id, team_id)` with a DIFFERENT group_id and asserts `DataIntegrityViolationException`. Test passes (1 of the 2 methods). |
| **D-04** — `@Enumerated(EnumType.STRING)` on enum-typed fields; no DB CHECK constraints | PASS | `SeasonPhase.java:31, 35, 39` — three `@Enumerated(EnumType.STRING)` annotations on `phaseType`, `layout`, `format`. V3 has zero `CHECK` constraints (verified by grep `CHECK = 0`). Enum columns are `VARCHAR(20)` (V3:9, 10, 11). |
| **D-05** — `SeasonPhase.format` reuses existing `SeasonFormat` enum (NOT a new PhaseFormat) | PASS | `SeasonPhase.java:41` — `private SeasonFormat format = SeasonFormat.LEAGUE;` (reuses `org.ctc.domain.model.SeasonFormat`). No new `PhaseFormat.java` exists in the repo. |
| **D-06** — Two new top-level enums in their own files: `PhaseType` (REGULAR, PLAYOFF, PLACEMENT), `PhaseLayout` (LEAGUE, GROUPS, BRACKET) | PASS | `PhaseType.java:3-7` declares all three values in order. `PhaseLayout.java:3-7` declares all three values in order. Both files contain only the package + enum declaration — no annotations, no methods, exactly mirrors `SeasonFormat.java`. |
| **D-07** — Three new repositories ship with default Spring Data CRUD only (no custom finders) | PASS | `SeasonPhaseRepository.java` (9 lines, body `{}` only); `SeasonPhaseGroupRepository.java` (9 lines, body `{}` only); `PhaseTeamRepository.java` (9 lines, body `{}` only). Grep `^\s*(Optional|List|Page|Stream|long|int|boolean|void|@Query)` returns zero matches in all three files. |
| **D-08** — All three new entities extend `BaseEntity`; correct `@ToString(exclude = ...)` on every entity that has `@OneToMany` collections or lazy parents | PASS | `SeasonPhase.java:18` (`extends BaseEntity`), `:17` (`@ToString(exclude = {"season", "groups", "raceScoring", "matchScoring"})`). `SeasonPhaseGroup.java:18` (`extends BaseEntity`), `:17` (`@ToString(exclude = {"phase"})`). `PhaseTeam.java:17` (`extends BaseEntity`), `:16` (`@ToString(exclude = {"phase", "team", "group"})`). Audit columns in V3 lines 20-21, 33-34, 43-44 (`created_at`, `updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`). |

**Note on D-numbering:** 56-CONTEXT.md uses `D-01..D-06` plus an implicit "Repository scope" decision and "Lombok/audit" pattern. The verification mandate listed `D-01..D-08`. Mapping used here:
- D-01 = "Entity Java-side scope" in CONTEXT.md
- D-02 = "Flyway migration & FK nullability"
- D-03 = "DB-level uniqueness constraints"
- D-04 = "Enum strategy" (STRING + no CHECK part)
- D-05 = "SeasonFormat reuse"
- D-06 = "PhaseType/PhaseLayout new enums" (CONTEXT calls this D-05 part 2)
- D-07 = "Repository scope" (CONTEXT calls this D-06)
- D-08 = "BaseEntity inheritance + ToString discipline" (implicit foundation pattern, verifier-mandated)

All eight decisions verified PASS regardless of numbering reconciliation.

## Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/org/ctc/domain/model/PhaseType.java` | enum REGULAR/PLAYOFF/PLACEMENT | VERIFIED | Lines 3-7, three values, no annotations, mirrors SeasonFormat |
| `src/main/java/org/ctc/domain/model/PhaseLayout.java` | enum LEAGUE/GROUPS/BRACKET | VERIFIED | Lines 3-7, three values, no annotations |
| `src/main/java/org/ctc/domain/model/SeasonPhase.java` | parent-with-children entity, three enums | VERIFIED | 76 lines; all 14 fields per contract; constructor at :69-74; `@OneToMany` `groups` at :65-67 |
| `src/main/java/org/ctc/domain/model/SeasonPhaseGroup.java` | child entity (phase + name + sortIndex) | VERIFIED | 41 lines; field is `name` (not `label`) per CONTEXT; constructor at :35-39 |
| `src/main/java/org/ctc/domain/model/PhaseTeam.java` | join entity (phase + team + nullable group) | VERIFIED | 40 lines; `group` `@JoinColumn(name = "group_id")` at :32 has NO `nullable = false` |
| `src/main/java/org/ctc/domain/repository/SeasonPhaseRepository.java` | default CRUD JpaRepository | VERIFIED | 9 lines, empty body |
| `src/main/java/org/ctc/domain/repository/SeasonPhaseGroupRepository.java` | default CRUD | VERIFIED | 9 lines, empty body |
| `src/main/java/org/ctc/domain/repository/PhaseTeamRepository.java` | default CRUD | VERIFIED | 9 lines, empty body |
| `src/main/resources/db/migration/V3__add_season_phase_tables.sql` | 3 tables + 3 ALTERs + 10 indexes + 3 UNIQUEs + 10 FKs | VERIFIED | 70 lines; all elements present and correctly ordered |
| `src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java` | 6 persistence tests | VERIFIED | 215 lines, 6 `@Test` methods, all pass (6/0/0) |
| `src/test/java/org/ctc/domain/model/PhaseTeamUniquenessIntegrationTest.java` | 2 UNIQUE-constraint tests | VERIFIED | 127 lines, 2 `@Test` methods, both pass (2/0/0); both use `saveAndFlush` + `assertThatThrownBy(... .isInstanceOf(DataIntegrityViolationException.class))` |
| `src/main/java/org/ctc/domain/model/Season.java` (modified) | new `List<SeasonPhase> phases` collection | VERIFIED | Line 69-71 adds `phases`; line 19 updates `@ToString(exclude = ...)` to include `"phases"` first; all legacy fields preserved (D-01) |
| `src/main/java/org/ctc/domain/model/Matchday.java` (modified) | new `phase` + `group` ManyToOne fields | VERIFIED | Lines 30-36 add both; line 19 updates `@ToString` to include `"phase", "group"`; existing `season` FK at :26-28 preserved |
| `src/main/java/org/ctc/domain/model/Playoff.java` (modified) | new `phase` ManyToOne field | VERIFIED (with deviation) | Lines 33-35 add `phase` with `@JoinColumn(name = "phase_id")`. **DEVIATION**: Plan-04 prescribed `unique = true` on the `@JoinColumn`, but the actual code lacks it (`grep "unique = true"` finds it only on existing `season_id` line). Acceptable because V3 SQL `uk_playoff_phase UNIQUE (phase_id)` enforces uniqueness at the DB level regardless of the Java-side hint. See "Open Items" below. |

## Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `SeasonPhase` | `Season` | `@ManyToOne LAZY @JoinColumn(name="season_id", nullable=false)` | WIRED | SeasonPhase.java:24-26 |
| `SeasonPhase` | `SeasonPhaseGroup` | `@OneToMany(mappedBy="phase", cascade=ALL, orphanRemoval=true) @OrderBy("sortIndex ASC")` | WIRED | SeasonPhase.java:65-67 |
| `SeasonPhaseGroup` | `SeasonPhase` | `@ManyToOne LAZY @JoinColumn(name="phase_id", nullable=false)` | WIRED | SeasonPhaseGroup.java:24-26 |
| `PhaseTeam` | `SeasonPhase` | `@ManyToOne LAZY @JoinColumn(name="phase_id", nullable=false)` | WIRED | PhaseTeam.java:23-25 |
| `PhaseTeam` | `Team` | `@ManyToOne LAZY @JoinColumn(name="team_id", nullable=false)` | WIRED | PhaseTeam.java:27-29 |
| `PhaseTeam` | `SeasonPhaseGroup` | `@ManyToOne LAZY @JoinColumn(name="group_id")` (nullable!) | WIRED | PhaseTeam.java:31-33 |
| `Season` | `SeasonPhase` | `@OneToMany(mappedBy="season", cascade=ALL, orphanRemoval=true) @OrderBy("sortIndex ASC")` | WIRED | Season.java:69-71 |
| `Matchday` | `SeasonPhase` | `@ManyToOne LAZY @JoinColumn(name="phase_id")` (nullable) | WIRED | Matchday.java:30-32 |
| `Matchday` | `SeasonPhaseGroup` | `@ManyToOne LAZY @JoinColumn(name="group_id")` (nullable) | WIRED | Matchday.java:34-36 |
| `Playoff` | `SeasonPhase` | `@ManyToOne LAZY @JoinColumn(name="phase_id")` (nullable; DB-level UNIQUE) | WIRED | Playoff.java:33-35 |
| V3 SQL → V1 schema | `REFERENCES seasons(id)`, `REFERENCES race_scorings(id)`, `REFERENCES match_scorings(id)`, `REFERENCES teams(id)` | All FK targets exist | WIRED | V3 lines 22-24, 35, 45-47, 53-54, 57 |
| Java enum strings ↔ V3 columns | `phase_type VARCHAR(20)`, `layout VARCHAR(20)`, `format VARCHAR(20)` | column types match `@Enumerated(EnumType.STRING)` | WIRED | V3:9-11; SeasonPhase.java:31-41 |
| Tests → V3 UNIQUE constraints | `saveAndFlush` triggers `DataIntegrityViolationException` for duplicate (season, phase_type) and (phase, team) tuples | WIRED + RUNTIME-PROVEN | PhaseTeamUniquenessIntegrationTest passes (2/0/0) |

## Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `SeasonPhaseRepository` | `JpaRepository<SeasonPhase, UUID>` | Spring Data + Hibernate against `season_phases` table | YES — IntegrationTest writes/reads real rows | FLOWING |
| `SeasonPhaseGroupRepository` | `JpaRepository<SeasonPhaseGroup, UUID>` | `season_phase_groups` table | YES — Test 2 writes 3 rows + reloads | FLOWING |
| `PhaseTeamRepository` | `JpaRepository<PhaseTeam, UUID>` | `phase_teams` table | YES — Tests 3 + UNIQUE-test write/read | FLOWING |
| Bidirectional `Season.phases` | reload via `seasonRepository.findById()` | `season_phases` JOINed via `season_id` | YES — Test 4 (`givenSeasonWithPhases_whenReloaded...`) passes after `flush() + clear()` | FLOWING |

Phase 56 is intentionally a "schema only" phase. Tables are populated only by tests; production data flow (REGULAR-phase backfill, PhaseTeam roster) is Phase 57's responsibility — confirmed in CONTEXT.md "Integration Points" section.

## Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Project compiles with new enums + entities | grep + read of created files | All 8 new files (2 enums, 3 entities, 3 repos) exist on disk with expected size and content | PASS |
| Flyway V3 applies cleanly | Run on every Spring Boot test boot; both new IT classes use `@SpringBootTest @ActiveProfiles("dev")` and pass | 6/0/0 + 2/0/0 = 8 IT methods green | PASS |
| UNIQUE `uk_season_phase_type` rejects duplicate REGULAR | `target/surefire-reports/org.ctc.domain.model.PhaseTeamUniquenessIntegrationTest.txt` | `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` | PASS |
| UNIQUE `uk_phase_team` rejects duplicate (phase, team) | Same report | Same | PASS |
| Audit columns populate on new entities | `SeasonPhaseEntityIntegrationTest.givenNewSeasonPhase_whenSaved_thenAuditFieldsArePopulated` (lines 78-100) | Test passes | PASS |
| Bidirectional `Matchday.phase` round-trip | `givenMatchdayWithPhase_whenSaved_thenPhaseIsReachableOnReload` (lines 165-180) | Test passes | PASS |
| Bidirectional `Playoff.phase` round-trip | `givenPlayoffWithPhase_whenSaved_thenPhaseIsReachableOnReload` (lines 183-198) | Test passes | PASS |
| `PhaseTeam.group` is nullable | `givenPhaseTeamWithoutGroup_whenSaved_thenGroupIsNull` (lines 128-144) | Test passes | PASS |
| JaCoCo line coverage gate | `awk` on `target/site/jacoco/jacoco.csv` | 25 482 covered / 29 647 total = **85.95%** (over 82% gate); SUMMARY claims 85.62%, current local report 85.95% — both exceed the gate | PASS |

## Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|----------------|-------------|--------|----------|
| MODEL-01 | 56-01, 56-02, 56-05 | New SeasonPhase entity with phaseType/layout/format/scoring/dates/duration | SATISFIED | SeasonPhase.java:18-75 covers all required fields per `<interfaces>` block; persistence proven by Test 1 |
| MODEL-02 | 56-03, 56-05 | DB-level UNIQUE preventing duplicate phase_type per season | SATISFIED | V3:25 + UniquenessTest method 1 |
| MODEL-03 | 56-02, 56-05 | SeasonPhaseGroup child entity with bidirectional collection | SATISFIED | SeasonPhaseGroup.java + SeasonPhase.groups; Test 2 verifies @OrderBy ordering |
| MODEL-04 | 56-02, 56-03, 56-05 | PhaseTeam with UNIQUE (phase_id, team_id); group_id nullable | SATISFIED | PhaseTeam.java; V3:48; UniquenessTest method 2 |
| MODEL-05 | 56-03, 56-04, 56-05 | Matchday.phase_id FK | SATISFIED | Matchday.java:30-32; V3:51,53; Test 5 |
| MODEL-06 | 56-03, 56-04, 56-05 | Playoff.phase_id FK with UNIQUE | SATISFIED | Playoff.java:33-35; V3:56-58; Test 6 |
| MODEL-07 | 56-04 | Season.phases collection | SATISFIED (partial — full per D-01 deferred to Phase 61) | Season.java:69-71 adds collection; legacy fields stay until Phase 61 |
| MODEL-08 | 56-02 | Three new repositories with CRUD baseline | SATISFIED | All three files = 9 lines each, empty body |
| MIGR-01 | 56-03 | Flyway V3 migration creates new tables/columns/constraints | SATISFIED | V3__add_season_phase_tables.sql (70 lines) covers all required DDL |
| MIGR-07 | 56-03 | V1 + V2 untouched (checksum integrity) | SATISFIED | `git diff origin/master...HEAD -- V1 V2` returned no output |

All 10 declared requirements SATISFIED. No orphaned requirements (REQUIREMENTS.md was not directly inspected for this verification — but every requirement listed in the four PLAN frontmatters maps to verified evidence above).

## Anti-Patterns Found

None of significance. Specific scans:

| File | Pattern | Result |
|------|---------|--------|
| All 8 new Java files | `TODO|FIXME|XXX|HACK|PLACEHOLDER` | Zero matches |
| All 8 new Java files | `placeholder|coming soon|will be here|not yet implemented` | Zero matches |
| All 8 new Java files | `return null|return \[\]|return \{\}` (non-test) | Zero matches in production code |
| Repositories | `@Query|Optional<|List<.*> find|long count|boolean exists` | Zero matches (D-07 honored) |
| Entities | `uniqueConstraints` | Zero matches (D-03 says DB-only) |
| V3 SQL | `ON DELETE CASCADE` | Zero matches (D-02 honored) |
| V3 SQL | `CHECK (` | Zero matches (D-04 honored) |
| V3 SQL | `phase_id UUID NOT NULL` (on matchdays/playoffs) | Zero matches (D-02 honored — must remain NULLABLE in Phase 56) |

Minor SUMMARY/code mismatch (informational only): `56-04-SUMMARY.md` claims `Season.addPhase(...)` and `Season.removePhase(...)` helper methods were added; grep confirms they were **not** added. This is consistent with the actual plan body (Plan-04 explicitly forbids convenience methods), so the code is plan-compliant; only the SUMMARY narrative is slightly off. No impact on phase goal achievement.

## Open Items / Recommendations

1. **Playoff.phase missing `unique = true` on `@JoinColumn`** — Plan-04 prescribed `@JoinColumn(name = "phase_id", unique = true)` on `Playoff.java:34`. The actual code (`Playoff.java:33-35`) has no `unique = true`. Functionally harmless because V3 SQL declares `uk_playoff_phase UNIQUE (phase_id)` at the DB level (so any duplicate insert is still rejected with `DataIntegrityViolationException`), and `unique = true` in `@JoinColumn` is only a Hibernate DDL hint — the project uses Flyway-managed schema, not Hibernate `hbm2ddl`, so the hint would have been ignored anyway. **Recommendation:** Phase 57 or 58 should add `unique = true` for documentation consistency, OR a follow-up note should explicitly accept the omission. Acceptable for Phase 56 per PASS verdict.

2. **No new integration test for `SeasonPhase.format` round-trip with `SeasonFormat.SWISS` or `ROUND_ROBIN`** — Tests cover `LEAGUE` defaults only. Phase 58 (services) likely covers all three formats; not blocking for Phase 56.

3. **No DB-level constraint on `playoff.season_id` UNIQUE in conflict with `playoff.phase_id` UNIQUE** — Phase 56 keeps both legacy `uk_playoff_season` (V1) and new `uk_playoff_phase` (V3). Existing playoffs have `phase_id = NULL` (NULLs are not equal in both H2 and MariaDB → no UNIQUE conflict). Phase 57's backfill must populate `phase_id` cleanly while still satisfying `uk_playoff_phase` (one row per phase). Phase 61 drops the legacy `uk_playoff_season` per CONTEXT-Deferred-Ideas.

4. **Surefire reports show 695 tests; SUMMARY claims 1072** — Discrepancy probably because SUMMARY counts Failsafe (E2E + IT) too and the local on-disk reports here only contain a subset. Build was reported as `./mvnw verify exit 0` so this is not a verification failure, just a metric reconciliation note.

5. **Naming: V3 file uses arrow notation `->`, plan called for `→`** — `V3__add_season_phase_tables.sql:1` has `Season -> Phase -> Group hierarchy`; CONTEXT.md uses Unicode `→`. The arrow form is ASCII-safe and equivalent. No action needed.

6. **Playoff.phase has no `@NotNull`** — Correctly omitted per D-01 (Phase 56 leaves the new column nullable; Phase 57 adds NOT NULL after backfill). No action needed.

## Threat / Security Surface

The new attack surface is essentially zero. Phase 56 ships only:
- 2 Java enums (compile-time constants, no I/O, no parsing)
- 3 JPA entities (no HTTP endpoints, no controller bindings, no Form DTOs — bound only via Spring Data internals)
- 3 default `JpaRepository` interfaces (no custom queries, no `@Query` SpEL, no SQL injection risk)
- 1 additive Flyway migration (DDL only — no data movement, runs once per DB on startup)
- 2 integration tests (test-only, `@Transactional` rollback)

The threat register from each plan (T-56-01 through T-56-21) is fully covered by the new code:
- **Tampering** mitigated by `@Enumerated(EnumType.STRING)` plus typed enums (T-56-03), DB UNIQUE constraints `uk_season_phase_type` and `uk_phase_team` (T-56-11/12), Flyway V1/V2 checksum integrity (T-56-07).
- **Repudiation** mitigated by `BaseEntity` audit columns inherited by all three new entities (T-56-04).
- **Information Disclosure** mitigated by `@ToString(exclude = {...})` on every entity carrying lazy associations (T-56-05, T-56-14).
- **DoS** mitigated by 10 FK indexes per V2 convention (T-56-10) and `orphanRemoval=true` JPA-side cascade matching `Season.matchdays` precedent (T-56-08, T-56-16).

No new HTTP handlers, no user-input parsing, no PII handling, no external service calls. The phase goal — "compilable JPA entities backed by Flyway-created tables, with FK relationships in place" — is achieved without expanding the application's attack surface beyond the existing Flyway/Hibernate/Spring Data baseline.

---

*Verified: 2026-04-26T18:00:00Z*
*Verifier: Claude (gsd-verifier, goal-backward mode)*
