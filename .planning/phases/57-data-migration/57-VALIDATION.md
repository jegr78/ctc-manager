---
phase: 57
slug: data-migration
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-27
mode: retroactive
---

# Phase 57 — Validation Strategy

> Retroactive Nyquist audit of an already-executed phase. Confirms that every locked requirement for the V4 data migration has automated verification — and that the residual manual-only check (MariaDB dialect runtime) is explicitly documented with a `Why Manual` rationale and has since been promoted to de-facto automated via the Phase 61 MariaDB CI smoke gate.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + Spring Boot Test 4.x + programmatic Flyway (`EmbeddedDatabaseBuilder`) |
| **Config file** | [pom.xml](../../pom.xml) — Surefire + Failsafe + JaCoCo, `<minimum>0.82</minimum>` line gate |
| **Quick run command** | `./mvnw test -Dtest=V4MigrateSeasonsToPhasesIT` (programmatic Flyway IT, ~2 s) |
| **Full suite command (no E2E)** | `./mvnw verify` — Surefire + JaCoCo gate |
| **MariaDB CI smoke** | [.github/workflows/mariadb-migration-smoke.yml](../../.github/workflows/mariadb-migration-smoke.yml) — boots MariaDB 11 service, runs Flyway V1→VN including V4, asserts `/actuator/health = UP` (added Phase 61, post-UAT-03) |
| **Coverage measured** | 87.90% line coverage at Phase 57 close (5028 / 5720) — 5.90 pp headroom over 0.82 threshold |
| **Estimated runtime** | ~2 s (programmatic IT only), ~90 s (verify, no E2E) |

---

## Sampling Rate

Phase 57 was already executed. The retroactive sampling contract:

- **After Plan 01 (TDD-RED):** `./mvnw test -Dtest=V4MigrateSeasonsToPhasesIT` exits NON-ZERO (expected RED state — V4 not yet created)
- **After Plan 02 (TDD-GREEN):** `./mvnw test -Dtest=V4MigrateSeasonsToPhasesIT` exits 0 (all 6 methods GREEN)
- **After Plan 03 (Smoke + Final Gate):** `./mvnw test -Dtest=V4MigrateSeasonsToPhasesIT,V4MigrationSmokeIT` exits 0; then `./mvnw verify` exits 0 with JaCoCo >= 82%
- **Before `/gsd-verify-work`:** Full `./mvnw verify` green AND JaCoCo line coverage >= 82%
- **Max feedback latency:** ~2 s (per-task programmatic IT), ~90 s (per-wave verify)

---

## Per-Task Verification Map

Phase 57 = 3 execution plans (01 = TDD-RED test, 02 = V4 production migration, 03 = smoke + final gate). The map covers every REQ-ID from the plan frontmatters and all observable behavioral dimensions of the migration.

| Task | Plan | Requirement | Behavior under test | Test Type | Test File / Evidence | Automated Command | Status |
|------|------|-------------|---------------------|-----------|----------------------|-------------------|--------|
| 57-01 / T1 | 01 | MIGR-02 | Each season gets exactly one REGULAR phase with all fields 1:1 copied (format, total_rounds, legs, event_duration_minutes, start_date, end_date, race_scoring_id, match_scoring_id, sort_index=0, layout=LEAGUE, label=null) | integration (programmatic Flyway) | [V4MigrateSeasonsToPhasesIT.java — `givenLegacySeasons_whenMigrationRuns_thenEachSeasonHasOneRegularPhase`](../../src/test/java/db/migration/V4MigrateSeasonsToPhasesIT.java#L113-L139) | `./mvnw test -Dtest='V4MigrateSeasonsToPhasesIT#givenLegacySeasons_whenMigrationRuns_thenEachSeasonHasOneRegularPhase'` | ✅ |
| 57-01 / T1 | 01 | MIGR-03 | Each Playoff linked to a new PLAYOFF phase via `playoffs.phase_id`; phase has D-08 defaults (sort_index=10, layout=BRACKET, format=LEAGUE, legs=1, label=playoff.name) and inherited scoring | integration (programmatic Flyway) | [V4MigrateSeasonsToPhasesIT.java — `givenLegacyPlayoff_whenMigrationRuns_thenPlayoffLinkedViaPhaseId`](../../src/test/java/db/migration/V4MigrateSeasonsToPhasesIT.java#L141-L173) | `./mvnw test -Dtest='V4MigrateSeasonsToPhasesIT#givenLegacyPlayoff_whenMigrationRuns_thenPlayoffLinkedViaPhaseId'` | ✅ |
| 57-01 / T1 | 01 | MIGR-04 | All `matchdays.phase_id` populated via correlated UPDATE; zero NULL `phase_id` rows after migration; each matchday's `phase_id` points to the REGULAR phase of its season | integration (programmatic Flyway) | [V4MigrateSeasonsToPhasesIT.java — `givenLegacyMatchdays_whenMigrationRuns_thenAllMatchdaysHavePhaseId`](../../src/test/java/db/migration/V4MigrateSeasonsToPhasesIT.java#L175-L200) | `./mvnw test -Dtest='V4MigrateSeasonsToPhasesIT#givenLegacyMatchdays_whenMigrationRuns_thenAllMatchdaysHavePhaseId'` | ✅ |
| 57-01 / T1 | 01 | MIGR-05 | `phase_teams` populated 1:1 from `season_teams`; count = 4; all rows have `group_id = NULL`; each row's `phase_id` points to a REGULAR-type phase for the correct season | integration (programmatic Flyway) | [V4MigrateSeasonsToPhasesIT.java — `givenLegacySeasonTeams_whenMigrationRuns_thenPhaseTeamsPopulated`](../../src/test/java/db/migration/V4MigrateSeasonsToPhasesIT.java#L202-L225) | `./mvnw test -Dtest='V4MigrateSeasonsToPhasesIT#givenLegacySeasonTeams_whenMigrationRuns_thenPhaseTeamsPopulated'` | ✅ |
| 57-01 / T1 | 01 | MIGR-02..05 (bridge column integrity) | Old bridge columns (`matchdays.season_id`, `playoffs.season_id`, `playoff_seasons` table) remain intact post-migration; legacy Season fields (format, total_rounds, legs) still populated | integration (programmatic Flyway) | [V4MigrateSeasonsToPhasesIT.java — `givenLegacyData_whenMigrationRuns_thenBridgeColumnsRemainIntact`](../../src/test/java/db/migration/V4MigrateSeasonsToPhasesIT.java#L227-L257) | `./mvnw test -Dtest='V4MigrateSeasonsToPhasesIT#givenLegacyData_whenMigrationRuns_thenBridgeColumnsRemainIntact'` | ✅ |
| 57-01 / T1 | 01 | MIGR-04 (NOT-NULL flip, D-12/D-13) | After V4 runs, `matchdays.phase_id` is NOT NULL; inserting a Matchday without `phase_id` violates the constraint (H2 dialect branch of `flipNotNullConstraints`) | integration (programmatic Flyway) | [V4MigrateSeasonsToPhasesIT.java — `givenMigratedSchema_whenMatchdayInsertedWithoutPhaseId_thenViolatesNotNullConstraint`](../../src/test/java/db/migration/V4MigrateSeasonsToPhasesIT.java#L259-L275) | `./mvnw test -Dtest='V4MigrateSeasonsToPhasesIT#givenMigratedSchema_whenMatchdayInsertedWithoutPhaseId_thenViolatesNotNullConstraint'` | ✅ |
| 57-03 / T1 | 03 | MIGR-02..05 (Spring context + JPA alignment) | Full Spring Boot context loads after V4 autoload; `SeasonRepository.findAll()` works; `Season.phases` collection is not null; a season with a V4-backfilled REGULAR phase exposes exactly 1 phase via JPA mapping | integration (`@SpringBootTest`) | [V4MigrationSmokeIT.java — `whenContextLoads_thenAllSeasonsHavePhases` + `givenSeasonWithBackfilledPhase_whenLoadedViaRepository_thenPhasesCollectionIsNotEmpty`](../../src/test/java/db/migration/V4MigrationSmokeIT.java#L91-L118) | `./mvnw test -Dtest=V4MigrationSmokeIT` | ✅ |
| 57-02 / T1 | 02 | MIGR-02..05 (V4 production class structure) | `V4__MigrateSeasonsToPhases` extends `BaseJavaMigration`; 5 private methods in D-02 order; D-05 fail-fast FlywayException on null scoring IDs; D-14 SLF4J logging via LoggerFactory; `SingleConnectionDataSource(connection, true)` per Pitfall-3 | compile + Surefire green | [V4__MigrateSeasonsToPhases.java](../../src/main/java/db/migration/V4__MigrateSeasonsToPhases.java) (226 lines); all 6 Plan-01 tests green proves correct structure | `./mvnw test -Dtest=V4MigrateSeasonsToPhasesIT` | ✅ |
| 57-VERIFICATION | re-verified | MIGR-04 (MariaDB DDL branch) | `flipNotNullConstraints` MariaDB branch (`MODIFY COLUMN phase_id UUID NOT NULL`) runs without error on real MariaDB 11 — V4 applies cleanly, `DESCRIBE matchdays`/`DESCRIBE playoffs` show `phase_id NOT NULL` | CI smoke (MariaDB) + Phase 61 UAT-03 de-facto | [mariadb-migration-smoke.yml](../../.github/workflows/mariadb-migration-smoke.yml) replays V1→VN on every push/PR; Phase 61 UAT-03 (commit `bed0ffd`) ran docker-compose MariaDB smoke with V4 confirmed PASS | GitHub Actions CI (automatic on every PR) | ✅ (de-facto automated) |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Sampling continuity:** All three plans have automated verification. No 3 consecutive tasks lack automated verify. Every code-touching plan (01, 02, 03) has targeted Surefire tests or `@SpringBootTest` smoke.

---

## Wave 0 Requirements

Phase 57 introduced two new test infrastructure patterns, both committed and green:

- [V4MigrateSeasonsToPhasesIT.java](../../src/test/java/db/migration/V4MigrateSeasonsToPhasesIT.java) — **first programmatic-Flyway integration test** in the codebase (EmbeddedDatabaseBuilder + `Flyway.configure().target(N)` harness); 355 lines, 6 @Test methods; new pattern: TDD-RED class with no @SpringBootTest, no @DataJpaTest — exercises real migration against isolated H2
- [V4MigrationSmokeIT.java](../../src/test/java/db/migration/V4MigrationSmokeIT.java) — **@SpringBootTest smoke in `db.migration` package**; requires explicit `classes = CtcManagerApplication.class` (deviation from plan's implicit configuration — documented in 57-03-SUMMARY.md); 2 @Test methods: vacuous-true `isNotNull()` context-load smoke + seeded-JPA mapping assertion `isNotEmpty()` for a directly-inserted post-V4 season

*Existing JUnit 5 + Spring Boot Test + H2 infrastructure covered all other needs — no new framework install.*

All Wave 0 assets are committed and green.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| MariaDB `MODIFY COLUMN phase_id UUID NOT NULL` DDL executes without error on real MariaDB instance and `DESCRIBE matchdays` confirms `Null=NO` after V4 runs | MIGR-04 (D-12 dialect branch) | **Production-Data-Boundary + DB-Migration-Dialect:** The H2 dialect branch of `flipNotNullConstraints` is exercised by `V4MigrateSeasonsToPhasesIT`; the MariaDB branch (`MODIFY COLUMN ... UUID NOT NULL`) cannot be exercised in CI because CI uses H2 only. Additionally, `UUID NOT NULL` type support in MariaDB 10.7+ (RESEARCH.md assumption A3) and the implicit-commit behavior of DDL in MariaDB (CR-03 in VERIFICATION.md) can only be confirmed on a real MariaDB instance. **Status: de-facto closed** — Phase 61 UAT-03 (commit `bed0ffd`) ran the full docker-compose MariaDB smoke and confirmed V4 applies cleanly; the CI gate [mariadb-migration-smoke.yml](../../.github/workflows/mariadb-migration-smoke.yml) now replays V1→VN against MariaDB 11 on every push/PR. The original human-manual item from 57-03-SUMMARY.md is promoted to automated for ongoing regression coverage. | (Closed — recorded for traceability) Boot fresh MariaDB (docker compose down -v && up db -d), set flyway.target=3 to apply V1–V3, insert seed data, remove target override, restart. Confirm Flyway log "V4 applied", then `DESCRIBE matchdays` and `DESCRIBE playoffs` must show `phase_id NOT NULL`. Run data-integrity selects from 57-03-SUMMARY.md steps 1-7. |
| Empty production-like DB boots cleanly without Flyway errors on dev profile | MIGR-02..05 | **Production-Data-Boundary:** The empty-DB guard in `flipNotNullConstraints` (added in Plan 02 to handle the case where DevDataSeeder runs after Flyway on H2) is exercised by the Surefire suite (DevDataSeeder fires on dev profile `@SpringBootTest`), but final dev/local profile launch is the truest signal of "no Flyway errors at startup" | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` → app must start without Flyway or JPA errors in console output |

**Why the manual residue is acceptable:** The single operationally-relevant manual item (MariaDB NOT NULL flip) is now de-facto automated via [mariadb-migration-smoke.yml](../../.github/workflows/mariadb-migration-smoke.yml) CI gate and Phase 61 UAT-03 evidence. The empty-DB boot check is a developer-facing startup sanity check, not a coverage gap — the behavior is exercised by the full `@SpringBootTest` Surefire suite on every `./mvnw verify` run (which starts the dev-profile Spring context, triggering DevDataSeeder after Flyway).

---

## Validation Sign-Off

- [x] All tasks have automated verify or are documented as manual-only with rationale
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (V4MigrateSeasonsToPhasesIT + V4MigrationSmokeIT both exist and pass)
- [x] No watch-mode flags (Surefire/Failsafe run-once)
- [x] Feedback latency < 15s (per-task programmatic IT ~2 s); < 90 s (full verify)
- [x] `nyquist_compliant: true` set in frontmatter
- [x] `wave_0_complete: true` set in frontmatter (V4MigrateSeasonsToPhasesIT + V4MigrationSmokeIT committed and green)
- [x] `mode: retroactive` set in frontmatter
- [x] `## Validation Audit 2026-05-07` appendix block present

**Approval:** approved 2026-05-07 (retroactive upgrade from draft — all REQ-IDs covered, no auto-fill needed)

---

## Validation Audit 2026-05-07

| Metric | Count |
|--------|-------|
| Requirements audited | 4 LOCKED (MIGR-02, MIGR-03, MIGR-04, MIGR-05) + 1 implicit constraint (MIGR-07 additive-migration invariant — V1/V2/V3 unmodified) |
| Plans audited | 3 (57-01, 57-02, 57-03) |
| Gaps found | 0 fillable |
| Resolved (already automated) | 4/4 LOCKED REQ-IDs; all covered by V4MigrateSeasonsToPhasesIT (6 methods) + V4MigrationSmokeIT (2 methods) + mariadb-migration-smoke.yml CI gate |
| Auto-filled in this audit | 0 (no new tests generated — all REQ-IDs were already covered laterally) |
| Escalated to manual-only | 1 (MariaDB dialect runtime — de-facto closed via Phase 61 UAT-03 + CI smoke gate; recorded for traceability) |
| Net-new test infrastructure | none (V4MigrateSeasonsToPhasesIT + V4MigrationSmokeIT were the Wave 0 deliverables of Phase 57 execution; no new tests generated in this audit) |

**Verdict:** **NYQUIST-COMPLIANT.** Every locked requirement (MIGR-02, MIGR-03, MIGR-04, MIGR-05) has automated verification via the programmatic Flyway integration test and the `@SpringBootTest` smoke test. The MariaDB dialect branch — the one genuine gap that existed at Phase 57 close — is now covered by the `mariadb-migration-smoke.yml` CI gate introduced in Phase 61 and the Phase 61 UAT-03 docker-compose smoke run. The existing `57-VALIDATION.md` draft has been upgraded to Phase-61 gold-standard depth: Per-Task Verification Map covers all REQ-IDs, Manual-Only table has `Why Manual` rationale, Wave 0 assets are inventoried, and the Validation Audit appendix is present.
