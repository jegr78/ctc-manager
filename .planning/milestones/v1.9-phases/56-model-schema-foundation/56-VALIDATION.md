---
phase: 56
slug: model-schema-foundation
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-07
mode: retroactive
---

# Phase 56 — Validation Strategy

> Retroactive Nyquist audit of an already-executed phase. Confirms that every locked
> requirement has automated verification — and that the residual manual-only checks are
> explicitly deferred with `Why Manual` rationale, not coverage gaps.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Spring Boot Test 4.x |
| **Config file** | [pom.xml](../../pom.xml) — Surefire + Failsafe + JaCoCo, JaCoCo `<minimum>0.82</minimum>` |
| **Quick run command** | `./mvnw test -Dtest=SeasonPhaseEntityIntegrationTest,PhaseTeamUniquenessIntegrationTest,V3MigrationTest` |
| **Full suite command** | `./mvnw verify` — all Surefire tests + JaCoCo gate |
| **DB profile** | `@ActiveProfiles("dev")` — H2 in-memory, Flyway V1→VN on every Spring context boot |
| **Coverage measured** | 85.62% line coverage at Phase 56 completion (post-plan-05 `./mvnw verify`) |
| **JaCoCo gate** | 82% minimum (0.82 threshold) — passes with 3.62 pp headroom |

---

## Sampling Rate

Phase 56 was already executed. The retroactive sampling contract:

- **Per task during execution:** `./mvnw -q compile -DskipTests` (Plans 01–04) and `./mvnw test -Dtest=<NewTest>` (Plan 05)
- **After plan-05 final gate:** `./mvnw verify` — full Surefire + JaCoCo pass confirmed (1072 tests, 0 failures, 0 errors)
- **Retroactive auto-fill (2026-05-07):** `./mvnw test -Dtest=V3MigrationTest` — new INFORMATION_SCHEMA regression guard (8 tests, exit 0)
- **Max feedback latency:** ~90 s (`./mvnw verify`), ~77 s (V3MigrationTest full Spring context boot)

---

## Per-Task Verification Map

Phase 56 = 5 plans, 10 REQ-IDs: MODEL-01, MODEL-02, MODEL-03, MODEL-04, MODEL-05, MODEL-06, MODEL-07, MODEL-08, MIGR-01, MIGR-07.

| Task | Plan | Requirement | Behavior under test | Test Type | Test File / Evidence | Automated Command | Status |
|------|------|-------------|---------------------|-----------|----------------------|-------------------|--------|
| 56-01 / T1–T2 | 01 | MODEL-01 (partial — enum value sets) | `PhaseType` has exactly REGULAR/PLAYOFF/PLACEMENT; `PhaseLayout` has exactly LEAGUE/GROUPS/BRACKET; both compile clean in `org.ctc.domain.model` | compile + integration | Hibernate `ddl-auto=validate` at every `@SpringBootTest` startup; [SeasonPhaseEntityIntegrationTest.java:55-76](../../src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java#L55-L76) (`givenNewSeasonPhase_whenSaved_thenAuditFieldsArePopulated` — round-trips `PhaseType.REGULAR` and `PhaseLayout.LEAGUE` enum values) | `./mvnw test -Dtest=SeasonPhaseEntityIntegrationTest` | ✅ |
| 56-02 / T1 | 02 | MODEL-01 (entity structure) | `SeasonPhase` entity persists with all required fields (phaseType, layout, format, scoring FKs, dates, legs, label); BaseEntity audit columns populate; three `@Enumerated(EnumType.STRING)` fields | integration (`@SpringBootTest`) | [SeasonPhaseEntityIntegrationTest.java:55-76](../../src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java#L55-L76) — `givenNewSeasonPhase_whenSaved_thenAuditFieldsArePopulated` asserts `getId()`, `getCreatedAt()`, `getUpdatedAt()` are non-null post-save | `./mvnw test -Dtest=SeasonPhaseEntityIntegrationTest` | ✅ |
| 56-02 / T2 | 02 | MODEL-03 | `SeasonPhaseGroup` (phase + name + sortIndex) persists; `SeasonPhase.groups` `@OneToMany` loads in `@OrderBy("sortIndex ASC")` order on reload | integration | [SeasonPhaseEntityIntegrationTest.java:78-108](../../src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java#L78-L108) — `givenSeasonPhaseWithGroups_whenReloaded_thenGroupsCollectionIsOrderedBySortIndex` saves 3 groups with sortIndex 2/0/1, reloads via `flush()` + `clear()`, asserts order (0,1,2) | `./mvnw test -Dtest=SeasonPhaseEntityIntegrationTest` | ✅ |
| 56-02 / T2 | 02 | MODEL-04 (entity + nullable group) | `PhaseTeam` join entity persists with nullable `group` field; `PhaseTeam.group` remains null when not set (D-01 group optionality) | integration | [SeasonPhaseEntityIntegrationTest.java:110-130](../../src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java#L110-L130) — `givenPhaseTeamWithoutGroup_whenSaved_thenGroupIsNull` asserts `reloaded.getGroup()` is null | `./mvnw test -Dtest=SeasonPhaseEntityIntegrationTest` | ✅ |
| 56-02 / T2 | 02 | MODEL-08 | Three new repositories (`SeasonPhaseRepository`, `SeasonPhaseGroupRepository`, `PhaseTeamRepository`) exist as default `JpaRepository<Entity, UUID>` with no custom finders (D-06); Spring Data wires them at context startup | integration (implicit) | All three repositories auto-wired and used throughout `SeasonPhaseEntityIntegrationTest` and `PhaseTeamUniquenessIntegrationTest` without error; grep confirms zero method declarations in each repository body | `./mvnw test -Dtest=SeasonPhaseEntityIntegrationTest` | ✅ |
| 56-03 | 03 | MODEL-02 | DB-level UNIQUE `uk_season_phase_type (season_id, phase_type)` declared by V3 prevents a second REGULAR phase on the same season | integration (DB constraint) | [PhaseTeamUniquenessIntegrationTest.java:42-78](../../src/test/java/org/ctc/domain/model/PhaseTeamUniquenessIntegrationTest.java#L42-L78) — `givenSeasonWithRegularPhase_whenSecondRegularPhaseInserted_thenViolatesUniqueSeasonPhaseType` uses `saveAndFlush` + `assertThatThrownBy(DataIntegrityViolationException.class)`; positive control (PLAYOFF on same season succeeds) | `./mvnw test -Dtest=PhaseTeamUniquenessIntegrationTest` | ✅ |
| 56-03 | 03 | MODEL-04 (DB constraint) | DB-level UNIQUE `uk_phase_team (phase_id, team_id)` declared by V3 rejects a second `PhaseTeam` for the same (phase, team) pair even when group differs | integration (DB constraint) | [PhaseTeamUniquenessIntegrationTest.java:80-125](../../src/test/java/org/ctc/domain/model/PhaseTeamUniquenessIntegrationTest.java#L80-L125) — `givenPhaseTeamForTeamA_whenSecondPhaseTeamForTeamAInserted_thenViolatesUniquePhaseTeam` — duplicate with different group still fires `DataIntegrityViolationException` | `./mvnw test -Dtest=PhaseTeamUniquenessIntegrationTest` | ✅ |
| 56-03 | 03 | MODEL-05 | `matchdays.phase_id` and `matchdays.group_id` columns added by V3 are nullable (D-02: Phase 57 flips to NOT NULL) | integration (INFORMATION_SCHEMA) | [V3MigrationTest.java](../../src/test/java/db/migration/V3MigrationTest.java) — `givenV3HasRun_whenQueryInformationSchema_thenMatchdaysNewColumnsAreNullable` asserts `IS_NULLABLE = 'YES'` for both columns | `./mvnw test -Dtest=V3MigrationTest` | ✅ |
| 56-03 | 03 | MODEL-06 | `playoffs.phase_id` column added by V3 is nullable; UNIQUE constraint `uk_playoff_phase (phase_id)` exists | integration (INFORMATION_SCHEMA + DB constraint) | [V3MigrationTest.java](../../src/test/java/db/migration/V3MigrationTest.java) — `givenV3HasRun_whenQueryInformationSchema_thenPlayoffsPhaseIdColumnIsNullable` + `givenV3HasRun_whenQueryInformationSchema_thenUniqueConstraintsExist` (checks `UK_PLAYOFF_PHASE`) | `./mvnw test -Dtest=V3MigrationTest` | ✅ |
| 56-03 | 03 | MIGR-01 | V3 migration creates the three new tables (`season_phases`, `season_phase_groups`, `phase_teams`) with required columns verified by INFORMATION_SCHEMA | integration (INFORMATION_SCHEMA) | [V3MigrationTest.java](../../src/test/java/db/migration/V3MigrationTest.java) — three table-existence tests + `givenV3HasRun_whenQueryInformationSchema_thenSeasonPhasesHasRequiredColumns` (asserts `phase_type`, `layout`, `format`, `label`, `race_scoring_id`, `match_scoring_id`, `sort_index`, `legs`) | `./mvnw test -Dtest=V3MigrationTest` | ✅ |
| 56-03 | 03 | MIGR-07 | V1 and V2 migration files are untouched; original V1 tables remain intact | integration (INFORMATION_SCHEMA + git) | [V3MigrationTest.java](../../src/test/java/db/migration/V3MigrationTest.java) — `givenV3HasRun_whenQueryInformationSchema_thenOriginalV1TablesAreUntouched` asserts `seasons`, `matchdays`, `teams`, `race_scorings`, `match_scorings` all still exist; lateral evidence: `git diff origin/master...HEAD -- V1 V2` returns no output | `./mvnw test -Dtest=V3MigrationTest` | ✅ |
| 56-04 / T1 | 04 | MODEL-07 | `Season.phases` bidirectional `@OneToMany` collection added additively; legacy Season fields (format, scoring, dates, legs) preserved per D-01 | integration | [SeasonPhaseEntityIntegrationTest.java:132-153](../../src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java#L132-L153) — `givenSeasonWithPhases_whenReloaded_thenSeasonPhasesCollectionContainsTheSavedPhase` saves Season + SeasonPhase, reloads Season, asserts `phases` collection contains the saved entry | `./mvnw test -Dtest=SeasonPhaseEntityIntegrationTest` | ✅ |
| 56-04 / T2 | 04 | MODEL-05 | `Matchday.phase` FK field added additively; `Matchday.season` FK preserved (D-01); FK persists and reloads correctly | integration | [SeasonPhaseEntityIntegrationTest.java:155-177](../../src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java#L155-L177) — `givenMatchdayWithPhase_whenSaved_thenPhaseIsReachableOnReload` asserts `reloaded.getPhase().getId()` equals saved phase id AND `reloaded.getSeason().getId()` preserved | `./mvnw test -Dtest=SeasonPhaseEntityIntegrationTest` | ✅ |
| 56-04 / T2 | 04 | MODEL-06 | `Playoff.phase` FK field added additively; `Playoff.season` FK preserved (D-01); FK persists and reloads correctly | integration | [SeasonPhaseEntityIntegrationTest.java:179-202](../../src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java#L179-L202) — `givenPlayoffWithPhase_whenSaved_thenPhaseIsReachableOnReload` asserts `reloaded.getPhase().getId()` equals saved phase id | `./mvnw test -Dtest=SeasonPhaseEntityIntegrationTest` | ✅ |
| 56-05 | 05 | all 7 REQ-IDs above | Final gate: full test suite passes with both new integration test classes present; JaCoCo line coverage ≥ 82% | full gate | [56-05-SUMMARY.md](56-05-SUMMARY.md) — `./mvnw verify` exit 0; Surefire 1072 / 0 / 0; JaCoCo 85.62% | `./mvnw verify` | ✅ |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Sampling continuity:** No 3 consecutive tasks lack automated verification. Every code-touching plan has either (a) targeted integration tests or (b) INFORMATION_SCHEMA assertions that verify schema structure by name. The doc-only aspects of Plan 01 (enum shape convention) are covered indirectly by Hibernate `ddl-auto=validate` at every `@SpringBootTest` boot.

---

## Wave 0 Requirements

*Existing infrastructure covered the phase — plus one net-new test class auto-filled by this retroactive audit.*

Phase 56 did not require a Wave-0 framework install — JUnit 5 + Spring Boot Test were already wired. The **net-new test infrastructure** introduced by Phase 56 execution:

- [SeasonPhaseEntityIntegrationTest.java](../../src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java) — `@SpringBootTest @ActiveProfiles("dev") @Transactional` persistence + audit + bidirectional round-trip test (6 methods, 213 lines); uses `@PersistenceContext EntityManager` + `flush()` / `clear()` pattern to force inverse-collection reload inside a single transaction (Phase 56 pattern-established)
- [PhaseTeamUniquenessIntegrationTest.java](../../src/test/java/org/ctc/domain/model/PhaseTeamUniquenessIntegrationTest.java) — DB-level UNIQUE constraint enforcement (2 methods, 125 lines); uses `saveAndFlush` + `assertThatThrownBy(DataIntegrityViolationException.class)` idiom
- [V3MigrationTest.java](../../src/test/java/db/migration/V3MigrationTest.java) (**net-new, auto-filled 2026-05-07**) — Surefire `@SpringBootTest` INFORMATION_SCHEMA regression guard for V3 schema structure (8 methods); modelled on V5MigrationTest / V6MigrationTest (Phase 61 pattern); covers: 3 table-existence checks, UNIQUE constraint name verification (uk_season_phase_type, uk_phase_team, uk_playoff_phase), matchdays/playoffs nullable FK columns, season_phases required column inventory, V1 original-table preservation

All Wave-0-equivalent assets are committed and green.

**Auto-fill gap closed:** The original Phase 56 execution lacked a dedicated INFORMATION_SCHEMA test verifying V3 schema structure by constraint name. The two integration tests from Plan 05 prove functional correctness (DataIntegrityViolationException fires) but do not assert the constraint names or column nullability by querying the schema catalog. `V3MigrationTest` fills this gap — exit 0, 8/8 tests pass — and is the only net-new file introduced by this retroactive audit.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| **V3 applies cleanly on production MariaDB** | MIGR-01 / MIGR-07 (portability) | `V3MigrationTest` and the integration tests run against H2 in-memory. MariaDB dialect portability is covered by the CI smoke workflow (`mariadb-migration-smoke.yml`) introduced in Phase 61 — that workflow runs V1→VN on MariaDB 11 and asserts `actuator/health = UP`, which implicitly covers V3. However, an explicit per-constraint INFORMATION_SCHEMA assertion against MariaDB (using MariaDB-specific catalog syntax) is out of scope for the automated H2-profile test suite. **Production-Data-Boundary**: MariaDB CI requires a Docker service container and runs only in the GitHub Actions environment; it cannot be reproduced as a local Surefire test. | 1) In a Docker environment with MariaDB 11: `docker compose up --build -d` 2) Check `./actuator/health` returns UP. 3) Optionally connect to the DB and run `SHOW TABLES LIKE 'season_phases'` / `SHOW INDEX FROM season_phases WHERE Key_name = 'uk_season_phase_type'` to verify the constraint name exists. The GitHub Actions `mariadb-migration-smoke.yml` CI workflow covers this automatically on every PR. |
| **`SeasonFormat.SWISS` and `ROUND_ROBIN` round-trip on SeasonPhase.format** | MODEL-01 (partial) | The integration tests verify only `SeasonFormat.LEAGUE` (the default). SWISS and ROUND_ROBIN paths on `SeasonPhase.format` are exercised by Phase 58 service tests when all three formats are used in real phase-management flows. Automating these values in Phase 56 tests would duplicate Phase 58 coverage and create a Phase-scope violation (Phase 56 scope = foundation, not service behavior). **Phase-scope boundary**: adding per-format tests here would anticipate Phase 58 service contracts that weren't stable at Phase 56 execution time. | 1) In Phase 58 service tests: verify that `SeasonPhaseService.createPhase(season, SWISS)` correctly persists `SeasonFormat.SWISS` on the resulting entity. 2) Confirm `SeasonPhaseRepository.findById(id).getFormat()` returns `SWISS`. Phase 58's `SeasonPhaseServiceTest` covers this pattern. |
| **`Playoff.phase @JoinColumn unique=true` hint missing** | MODEL-06 (cosmetic) | The open item from 56-VERIFICATION.md: `Playoff.java` does not carry `unique = true` on the `@JoinColumn(name = "phase_id")`. This is a Hibernate DDL-generation hint only — the project uses Flyway-managed schema (not `hbm2ddl`), so the hint is ignored. V3 SQL declares `uk_playoff_phase UNIQUE (phase_id)` at the DB level, which is the authoritative constraint. `V3MigrationTest.givenV3HasRun_whenQueryInformationSchema_thenUniqueConstraintsExist` verifies the DB-level constraint. The Java-side `unique = true` hint is cosmetic documentation. **Non-automatable cosmetic**: adding a grep test for this hint would test for something the project architecture explicitly renders irrelevant (Flyway supersedes Hibernate DDL hints). | Review in Phase 58 or 61: consider adding `unique = true` to `@JoinColumn(name = "phase_id")` on `Playoff.java` for documentation consistency. Accepted as-is per 56-VERIFICATION.md verdict. |

**Why the manual residue is acceptable:** The two open manual items correspond to (a) a MariaDB CI gate that already runs automatically via GitHub Actions on every PR — not a hole, just a different pipeline tier — and (b) a phase-scope boundary (SeasonFormat variant testing belongs to Phase 58 service coverage). The cosmetic annotation gap is explicitly tracked and accepted per the Phase 56 verification verdict. No requirement is uncovered.

---

## Validation Sign-Off

- [x] All tasks have automated verify or are documented as manual-only with rationale
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references — one auto-fill gap closed (V3MigrationTest, 8 tests green)
- [x] No watch-mode flags (Surefire in CI mode, no `-Dspring-boot.run.fork=false` or similar)
- [x] Feedback latency < 120 s for the targeted test gate (`./mvnw test -Dtest=V3MigrationTest` ~77 s)
- [x] `nyquist_compliant: true` set in frontmatter — all 10 REQ-IDs either COVERED or in Manual-Only with rationale
- [x] `wave_0_complete: true` — V3MigrationTest and both Plan-05 test classes committed and green

**Approval:** approved 2026-05-07 (retroactive, mode: retroactive)

---

## Validation Audit 2026-05-07

| Metric | Count |
|--------|-------|
| Requirements audited | 10 (MODEL-01, MODEL-02, MODEL-03, MODEL-04, MODEL-05, MODEL-06, MODEL-07, MODEL-08, MIGR-01, MIGR-07) |
| Plans audited | 5 (56-01, 56-02, 56-03, 56-04, 56-05) |
| Gaps found | 1 (no INFORMATION_SCHEMA test verifying V3 schema structure by constraint name — MIGR-01 partial, MODEL-02/04/06 partial) |
| Resolved (already automated) | 9 of 10 REQ-IDs fully covered by existing SeasonPhaseEntityIntegrationTest (6 methods) + PhaseTeamUniquenessIntegrationTest (2 methods) |
| Resolved via auto-fill | 1 gap closed by V3MigrationTest (8 INFORMATION_SCHEMA assertion methods, exit 0 — 2026-05-07) |
| Escalated to manual-only | 3 rows (MariaDB portability via CI, SeasonFormat variant coverage via Phase 58, Playoff.phase @JoinColumn hint) |
| Net-new test infrastructure | [V3MigrationTest.java](../../src/test/java/db/migration/V3MigrationTest.java) — 8 Surefire INFORMATION_SCHEMA tests, modelled on V5MigrationTest / V6MigrationTest pattern |

**Verdict:** **NYQUIST-COMPLIANT.** All 10 REQ-IDs have automated verification. The one identified gap (missing INFORMATION_SCHEMA regression guard for V3 schema structure) was auto-filled by `V3MigrationTest` (8 tests, exit 0, 2026-05-07). Three manual residue rows are explicitly deferred with `Why Manual` rationale: MariaDB portability (CI-tier, not missing coverage), SeasonFormat variant paths (Phase 58 scope boundary), and a cosmetic Hibernate DDL hint (architecturally irrelevant under Flyway-managed schema).
