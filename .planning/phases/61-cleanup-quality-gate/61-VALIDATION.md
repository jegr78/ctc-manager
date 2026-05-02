---
phase: 61
slug: cleanup-quality-gate
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-02
mode: retroactive
---

# Phase 61 — Validation Strategy

> Retroactive Nyquist audit of an already-executed phase. Confirms that every locked requirement, gap-closure regression, and post-UAT fix has automated verification — and that the residual manual-only checks are explicitly deferred per the verifier's `human_verification:` block, not coverage gaps.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Spring Boot Test 4.x + Playwright (compile scope, runtime via Failsafe) |
| **Config file** | [pom.xml](../../pom.xml) — Surefire + Failsafe + JaCoCo, JaCoCo `<minimum>0.82</minimum>` (line 1147 region, count = 1) |
| **Quick run command** | `./mvnw test` (Surefire only) or `./mvnw verify -Dit.test=…` for a single IT |
| **Full suite command (no E2E)** | `./mvnw verify` — 1172 Surefire + JaCoCo gate (~1–2 min on dev hardware) |
| **Full suite command (with E2E)** | `./mvnw verify -Pe2e` — adds 31 Failsafe / Playwright tests (~4:40 min) |
| **MariaDB CI smoke** | [.github/workflows/mariadb-migration-smoke.yml](../../.github/workflows/mariadb-migration-smoke.yml) — boots MariaDB 11 service, runs Flyway V1→VN, asserts `/actuator/health = UP` (added post-UAT-03 to close the pre-merge gate that allowed V5+V6 to ship without MariaDB exposure) |
| **Coverage measured** | 85.17% line coverage (5483 / 6438) at HEAD post-UAT fixes — 3.17 pp headroom over the 0.82 threshold |
| **Estimated runtime** | ~280 s (full E2E gate); ~80 s (verify, no E2E) |

---

## Sampling Rate

Phase 61 was already executed. The retroactive sampling contract:

- **After every gap-fix commit (during execution):** Targeted Surefire (`./mvnw test -Dtest=…`) — feedback < 30 s
- **After every plan wave:** `./mvnw verify` (Surefire + JaCoCo) — feedback < 90 s
- **Before `/gsd-verify-work`:** `./mvnw verify -Pe2e` must be green — feedback < 5 min
- **Before PR merge:** Same as above + the new MariaDB CI workflow on the migration files
- **Max feedback latency observed:** 4:40 min (gap-09 final gate)

---

## Per-Task Verification Map

Phase 61 = 5 base plans + 9 gap-closure plans + 2 post-UAT fix commits. The map below collapses to one row per locked requirement / regression dimension and points at the test file proving coverage.

| Task | Plan | Requirement | Behavior under test | Test Type | Test File / Evidence | Automated Command | Status |
|------|------|-------------|---------------------|-----------|----------------------|-------------------|--------|
| 61-01 | 01 | doc-only (D-04) | ROADMAP-SC1 + PROJECT.md key-decision row note bridge-column drop | n/a (docs) | [.planning/ROADMAP.md](../../.planning/ROADMAP.md), [.planning/PROJECT.md](../../.planning/PROJECT.md) | n/a | ✅ |
| 61-02 | 02 | MIGR-06 / D-01..D-06 | 8 Season fields removed; Matchday/Playoff bridge field gone; Convenience-getter `getSeason()` preserved as first-class API; ~14 callsites + templates migrated; compile clean under `ddl-auto=validate` | compile + unit | Hibernate validate at `@SpringBootTest` startup; targeted [MatchdayServiceTest.java](../../src/test/java/org/ctc/domain/service/MatchdayServiceTest.java), [CsvImportServiceTest.java](../../src/test/java/org/ctc/dataimport/CsvImportServiceTest.java); 1172 Surefire green | `./mvnw test` | ✅ |
| 61-02 / G1 / CR-01 | 02 | MIGR-06 / "free of dead references" | Cross-phase finder regression: `MatchdayService.createInline` + `CsvImportService.findOrCreateMatchday`/`checkDuplicate` use phase-scoped `findByPhaseIdOrderBySortIndexAsc(regular.id)` instead of the season-scoped finder that poisoned `sortIndex` across REGULAR+PLAYOFF phases | unit | [MatchdayServiceTest.java:219,257,277-309](../../src/test/java/org/ctc/domain/service/MatchdayServiceTest.java#L219-L309), [MatchdayServiceTest.java:498-506](../../src/test/java/org/ctc/domain/service/MatchdayServiceTest.java#L498-L506) (post-playoff path), [CsvImportServiceTest.java:457-459](../../src/test/java/org/ctc/dataimport/CsvImportServiceTest.java#L457-L459) | `./mvnw test -Dtest='MatchdayServiceTest,CsvImportServiceTest'` | ✅ |
| 61-03 | 03 | MIGR-06 / D-07..D-10 | V6 drops 8 seasons cols + 2 bridge FK cols (`matchdays.season_id`, `playoffs.season_id`) + `playoff_seasons` join table; Hibernate JPA mapping still validates post-V6 | integration (Surefire `@SpringBootTest`) | [V6MigrationTest.java](../../src/test/java/db/migration/V6MigrationTest.java) — 4 `@Test` methods including `JpaMappingStillWorks` | `./mvnw test -Dtest=V6MigrationTest` | ✅ |
| 61-03 / UAT-03 | post-UAT (commit 6db56d4) | MIGR-06 + portability | V5 + V6 are dialect-aware Java migrations; H2 branch keeps `ALTER COLUMN ... DROP NOT NULL` / index drops, MariaDB branch uses `MODIFY COLUMN <name> UUID NULL` and relies on auto-drop of FK indexes | integration (Surefire) + CI smoke | [V5MigrationTest.java](../../src/test/java/db/migration/V5MigrationTest.java) (`INFORMATION_SCHEMA` assertion: `season_phases.race_scoring_id` + `match_scoring_id IS_NULLABLE = 'YES'`); [V6MigrationTest.java](../../src/test/java/db/migration/V6MigrationTest.java) (dialect-agnostic queries); [mariadb-migration-smoke.yml](../../.github/workflows/mariadb-migration-smoke.yml) (boots real MariaDB 11) | `./mvnw test -Dtest='V5MigrationTest,V6MigrationTest'` + GitHub Action on PR | ✅ |
| 61-04 | 04 | QUAL-02 / D-11..D-16 | GROUPS-layout season E2E: season + 2-group phase creation, driver import (group resolved via PhaseTeam shortName), per-group matchday + race scaffolding, race-result UI entry, per-group + combined-view standings render | E2E (Failsafe / Playwright) | [GroupsSeasonE2ETest.java](../../src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java) — 520 lines, 1 `@Test`, `@Import(TestGoogleSheetsConfig)` returns 12 deterministic driver rows | `./mvnw verify -Pe2e -Dit.test=GroupsSeasonE2ETest` | ✅ |
| 61-05 | 05 | QUAL-03 / D-17..D-19 | Legacy V4-migrated season opens cleanly: exactly 1 REGULAR phase tab (+ optional PLAYOFF tab), all matchdays accessible, race detail loads, legacy `?seasonId=` URL auto-redirects to REGULAR phase view | E2E (Failsafe / Playwright) | [LegacyMigratedSeasonE2ETest.java](../../src/test/java/org/ctc/e2e/LegacyMigratedSeasonE2ETest.java) — 145 lines, 2 `@Test`, `@Sql BEFORE_TEST_METHOD`; fixtures [legacy-season-without-playoff.sql](../../src/test/resources/sql/legacy-season-without-playoff.sql) + [legacy-season-with-playoff.sql](../../src/test/resources/sql/legacy-season-with-playoff.sql) | `./mvnw verify -Pe2e -Dit.test=LegacyMigratedSeasonE2ETest` | ✅ |
| 61-05 | 05 | QUAL-01 / D-20..D-21 | JaCoCo line coverage ≥ 82 %; `<minimum>` unchanged at 0.82 (no Threshold-Senkung) | meta-gate | [pom.xml](../../pom.xml) JaCoCo plugin; report at `target/site/jacoco/index.html` | `./mvnw verify` (gate enforced) | ✅ (85.17 %) |
| 61-gap-01/02/03 | gap-01..03 | "free of stale comments" (G2) | Stale phase-narrative comments stripped from `domain.model` / `domain.repository` / `domain.service` / `admin.controller` / `admin.service` / `dataimport` / `sitegen` / `gt7sync` and 43 src/test files | compile + grep gate | 4 grep gates from [61-VERIFICATION.md](61-VERIFICATION.md) "Behavioral Spot-Checks" return 0 hits in `src/main/java` + `src/test/java`; one documented exception (V6 SQL header — Flyway-immutable) | `grep -rn -E 'Phase [56][0-9] (MIGR-06\|D-[0-9]+\|WR-0?[0-9]+\|CR-0?[0-9]+\|IN-0?[0-9]+)' src/main/java src/test/java` | ✅ (manual gate, see Manual-Only) |
| 61-gap-04/05 | gap-04..05 | "accurate javadoc on non-obvious public APIs" (G5) | `MatchdayRepository` finders document phase-vs-season distinction (CR-01 contract); `PlayoffService.createPlayoff` documents atomic auto-creation; `SeasonManagementService.save` documents REGULAR-phase bootstrap; `StandingsController.standings` documents 4-tier resolution priority; `StandingsService.calculateBuchholzScoresForPhase` unused `groupId` parameter dropped | compile + targeted Surefire | 79 targeted Surefire tests green per [61-gap-04-SUMMARY.md](61-gap-04-SUMMARY.md); javadoc presence verified by grep in [61-VERIFICATION.md](61-VERIFICATION.md) Key Link Verification table | `./mvnw test` | ✅ |
| 61-gap-06/07/08 | gap-06..08 | "free of dead code branches + appropriate validation boundaries" (G3, G4) | `PlayoffService.playoffSeedRepository` + `StandingsService.teamRepository` removed (closes deferred-items.md backlog); 22 unused imports removed across 14 files; 0 hits on `Objects.requireNonNull` / `Validate.notNull` / `Assert.notNull` / `Preconditions.checkNotNull` in `src/main/java`; remaining `if (x == null)` lines classified KEEP-boundary or KEEP-sentinel with rationale | compile + Surefire (61 + 71 targeted tests green) | [61-gap-06-SUMMARY.md](61-gap-06-SUMMARY.md), [61-gap-08-SUMMARY.md](61-gap-08-SUMMARY.md) classification tables | `./mvnw test` | ✅ |
| 61-gap-09 | gap-09 | final gate | `./mvnw verify -Pe2e` BUILD SUCCESS — 1172 Surefire + 31 Failsafe, 0 failures, 0 errors, 1 skipped | full gate | [61-gap-09-SUMMARY.md](61-gap-09-SUMMARY.md) — commit `461bc16` | `./mvnw verify -Pe2e` | ✅ |
| post-UAT | UAT-01 (commit f5b10bc) | template regression | `season-phase-form.html` Phase Type / Layout / Format dropdowns render non-empty option labels (Thymeleaf `${labels.get(enum)}` instead of `[enum]` bracket-indexer that resolved to null on `Map<Enum, String>`) | integration (`@WebMvcTest`-style) | [SeasonPhaseControllerIT.java:76 — `givenExistingPhase_whenGetEditForm_thenDropdownOptionsHaveNonEmptyLabels`](../../src/test/java/org/ctc/admin/controller/integration/SeasonPhaseControllerIT.java#L76); asserts all 8 expected label strings (`Regular Season`, `Playoff`, `Placement`, `League`, `Groups`, `Bracket`, `Swiss`, `Round Robin`) are present in the rendered HTML | `./mvnw test -Dtest='SeasonPhaseControllerIT#givenExistingPhase_whenGetEditForm_thenDropdownOptionsHaveNonEmptyLabels'` | ✅ |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Sampling continuity:** No 3 consecutive tasks lack automated verification. Every code-touching plan and every gap-fix plan has either (a) targeted unit/integration tests, (b) compile + JPA-validate at `@SpringBootTest` startup, or (c) explicit grep audit gates documented in [61-VERIFICATION.md](61-VERIFICATION.md). Doc-only plans (61-01) are correctly excluded from automated verification.

---

## Wave 0 Requirements

*Existing infrastructure covered all phase requirements.*

Phase 61 did not need a Wave-0 framework install — JUnit 5 + Mockito + Playwright + Spring Boot Test were already wired through prior milestones. The only **net-new test infrastructure** introduced by Phase 61 (and counted as Wave-0-equivalent for retroactive purposes):

- [V6MigrationTest.java](../../src/test/java/db/migration/V6MigrationTest.java) — Surefire `@SpringBootTest` shell for Flyway INFORMATION_SCHEMA assertions (D-09 pattern)
- [V5MigrationTest.java](../../src/test/java/db/migration/V5MigrationTest.java) — added post-UAT-03 to mirror V6 contract
- [GroupsSeasonE2ETest.java](../../src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java) `TestGoogleSheetsConfig` — `@TestConfiguration` + `@Bean @Primary GoogleSheetsService` stub returning 12 deterministic driver rows (D-12 pattern)
- [LegacyMigratedSeasonE2ETest.java](../../src/test/java/org/ctc/e2e/LegacyMigratedSeasonE2ETest.java) `@Sql` fixture pair — post-V6 schema legacy-season seeds (D-17 pattern)
- [.github/workflows/mariadb-migration-smoke.yml](../../.github/workflows/mariadb-migration-smoke.yml) — net-new CI gate (post-UAT hardening, commit `bed0ffd`); closes the pre-merge dialect-portability gap that allowed both V5 (Phase 60 escape) and V6 (Phase 61 escape) to ship untested against MariaDB

All Wave-0-equivalent assets are committed and green.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| **UAT Test 2 — Legacy migrated season visual smoke check (populated standings)** | QUAL-03 sub-aspect | [LegacyMigratedSeasonE2ETest](../../src/test/java/org/ctc/e2e/LegacyMigratedSeasonE2ETest.java) fixtures seed **0 race-results** (read-only path per Plan 61-05 D-18). The empty-state standings rendering path is exercised by the automated test; the **populated-standings rendering path** (real production data) is intentionally out of scope for the regression test. The user explicitly deferred this item ("kann ich so aktuell nicht machen, verschiebe ich auf später", UAT 2026-05-02). | 1) Boot a profile that points at a database carrying a real pre-v1.9 season migrated by V4 (e.g. `local` or a snapshot of prod). 2) Open `/admin/seasons/{id}` for that season. 3) Confirm exactly 1 REGULAR-phase tab (+ optional PLAYOFF tab). 4) Click into matchdays and races; confirm race-detail renders results. 5) Open the standings table and visually compare the rendered values against expectations from the source data — both with and without playoff. |
| **(Closed during UAT — recorded for traceability)** UAT Test 1 GROUPS visual smoke | QUAL-02 visual sanity | Re-verified PASS post-fix `f5b10bc` after the dropdown-rendering blocker (UAT-01) was resolved (UAT 2026-05-02). | n/a — closed |
| **(Closed during UAT — recorded for traceability)** UAT Test 3 V6 on MariaDB | MIGR-06 portability | Resolved via dialect-aware Java migrations (commit `6db56d4`); now also covered by [mariadb-migration-smoke.yml](../../.github/workflows/mariadb-migration-smoke.yml) CI gate, so this item is **promoted from manual-only to automated** at the Phase-62-onwards baseline. | n/a — closed |
| **(Closed during UAT — recorded for traceability)** UAT Test 4 Legacy URL bookmark regression | D-03 Tracked Behavior Change | Confirmed PASS by user (UAT 2026-05-02). | n/a — closed |

**Why the manual residue is acceptable:** The single open manual item (Test 2) is a visual sanity check against real production data — the automated `LegacyMigratedSeasonE2ETest` already proves the schema-migrated read-only render path works against deterministic fixtures. The populated-standings rendering uses the same controller + template surface as the empty-state path; the manual smoke covers data-shape edge cases that fixture seeds cannot economically reproduce. Per `superpowers:test-driven-development` + CLAUDE.md "validate at system boundaries," this is a real boundary check (production data shape), not a test-coverage hole.

---

## Validation Sign-Off

- [x] All tasks have automated verify or are documented as manual-only with rationale
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (none — phase used existing JUnit / Failsafe / Playwright infra; net-new tests + the MariaDB CI workflow are inventoried above)
- [x] No watch-mode flags (Surefire / Failsafe in CI mode)
- [x] Feedback latency < 300 s for the full E2E gate (measured 4:40 min)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-05-02

---

## Validation Audit 2026-05-02

| Metric | Count |
|--------|-------|
| Requirements audited | 4 LOCKED (MIGR-06, QUAL-01, QUAL-02, QUAL-03) + 5 gap dimensions (G1/CR-01, G2, G3, G4, G5) + 2 post-UAT fixes (UAT-01, UAT-03) |
| Plans audited | 5 base (61-01..05) + 9 gap (61-gap-01..09) |
| Gaps found | 0 fillable |
| Resolved (already automated) | 11/11 verification dimensions |
| Escalated to manual-only | 1 (UAT Test 2 — populated-standings smoke against real data; deferred by user) |
| Net-new test infrastructure inventoried | V5MigrationTest, V6MigrationTest, GroupsSeasonE2ETest + TestGoogleSheetsConfig, LegacyMigratedSeasonE2ETest + 2 SQL fixtures, mariadb-migration-smoke.yml |

**Verdict:** **NYQUIST-COMPLIANT.** Every locked requirement and every gap-closure regression has automated verification. The single manual residue is explicitly deferred per the user's UAT decision and corresponds to a production-data boundary check that fixture-based tests cannot cover economically.
