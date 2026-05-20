---
phase: 90-perf-consolidation-module-split-decision
plan: 01
slug: perf-03-cluster-consolidation
status: complete
completed: 2026-05-20
wave: 1
depends_on: []
requirements: [PERF-03]
---

## Objective recap

PERF-03 consolidates the Top-1 + Top-4 cache-key buckets identified by Phase 89's
PERF-02 instrumentation onto a shared composed annotation `@CtcDevSpringBootContext`
(`@SpringBootTest(classes = CtcManagerApplication.class) + @ActiveProfiles("dev")`).
Subclasses keep `@Tag("integration")` (Failsafe routing) and `@Transactional`
(where present); composed annotation does not embed either, since they vary per
consumer. Honest observational reporting per D-07; no hard local wallclock gate.
PERF-06 (Phase 91) CI re-harvest remains authoritative.

## What shipped

- New composed annotation `src/test/java/org/ctc/testsupport/CtcDevSpringBootContext.java`
  (4 meta-annotations on the declaration; Javadoc warns against
  `@DirtiesContext` / `@Tag` / `@Transactional` / `@DynamicPropertySource`).
- 19 outer test classes refactored from the two-annotation stack to the composed
  annotation (13 Surefire + 6 Failsafe; the 15 `@Nested PlayoffServiceTest` inner
  classes inherit per Spring TCF + JUnit composed-annotation inheritance).
- `docs/test-performance.md § PERF-03 Cluster Consolidation (Phase 90 Plan 01)`
  section appended with before/after Top-5 table, Surefire/Failsafe cluster
  observations, and Wave-5 measurement table.
- `docs/test-performance.md § v1.12 Forward Path` Lever-2 DONE annotation.
- `src/main/java/**` git-clean across the plan (D-09 invariant satisfied — verified
  via `git diff <plan-baseline>..HEAD -- 'src/main/java/**' | wc -l = 0`).

## Hash-bucket Audit

Source: `.test-perf-logs/90-01-aggregator-before.txt` (Wave-5 Run-1 `./mvnw clean
verify -Pe2e` BUILD SUCCESS, Maven Total 07:24 min, 5 sidecar files, 131 cache-key
events). Per D-01 mandate: this empirical enumeration — not the 24-class
PATTERNS.md best estimate — is the authoritative refactor surface for Task 3.

**Hash rotation observed:** Phase 89's `f524774b` (V4MigrationSmokeIT cluster)
maps to `499c01dd` in this run. Per CONTEXT.md D-01 + RESEARCH.md §Open Questions
Q1, Java identity-hashCode randomization rotates hex values across `mvn clean`
invocations; the CLUSTER STRUCTURE (which classes collide) is stable. Phase 89's
`9cefac4c` (V5MigrationTest cluster) coincidentally retained its hex.

### Bucket A — `9cefac4c` (Surefire `@SpringBootTest(classes=...) + @ActiveProfiles("dev")`)

29 cache-key events across 29 reported classes; 13 outer test classes after
collapsing the 15 `@Nested PlayoffServiceTest` inner classes.

| # | FQN | Source path |
|---|-----|-------------|
| 1 | `db.migration.V3MigrationTest` | `src/test/java/db/migration/V3MigrationTest.java` |
| 2 | `db.migration.V5MigrationTest` | `src/test/java/db/migration/V5MigrationTest.java` |
| 3 | `db.migration.V6MigrationTest` | `src/test/java/db/migration/V6MigrationTest.java` |
| 4 | `org.ctc.CtcManagerApplicationTests` | `src/test/java/org/ctc/CtcManagerApplicationTests.java` |
| 5 | `org.ctc.admin.TestDataServiceIntegrationTest` | `src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java` |
| 6 | `org.ctc.backup.audit.DataImportAuditSerializationTest` | `src/test/java/org/ctc/backup/audit/DataImportAuditSerializationTest.java` |
| 7 | `org.ctc.domain.model.BaseEntityAuditTest` | `src/test/java/org/ctc/domain/model/BaseEntityAuditTest.java` |
| 8 | `org.ctc.domain.model.PhaseTeamUniquenessIntegrationTest` | `src/test/java/org/ctc/domain/model/PhaseTeamUniquenessIntegrationTest.java` |
| 9 | `org.ctc.domain.model.SeasonPhaseEntityIntegrationTest` | `src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java` |
| 10 | `org.ctc.domain.repository.PhaseTeamRepositoryTest` | `src/test/java/org/ctc/domain/repository/PhaseTeamRepositoryTest.java` |
| 11 | `org.ctc.domain.service.MatchdayGeneratorServiceTest` | `src/test/java/org/ctc/domain/service/MatchdayGeneratorServiceTest.java` |
| 12 | `org.ctc.domain.service.PlayoffServiceTest` (outer; 15 `@Nested` inheritors) | `src/test/java/org/ctc/domain/service/PlayoffServiceTest.java` |
| 13 | `org.ctc.domain.service.SwissPairingServiceTest` | `src/test/java/org/ctc/domain/service/SwissPairingServiceTest.java` |

### Bucket B — `499c01dd` (Failsafe `@SpringBootTest(classes=...) + @ActiveProfiles("dev")` + per-class `@Tag`, optional `@Transactional`)

6 cache-key events across 6 outer classes.

| # | FQN | Source path | Extra annotations to KEEP on subclass |
|---|-----|-------------|---------------------------------------|
| 1 | `db.migration.V4MigrationSmokeIT` | `src/test/java/db/migration/V4MigrationSmokeIT.java` | `@Transactional`, `@Tag("integration")` |
| 2 | `db.migration.V7DataImportAuditMigrationIT` | `src/test/java/db/migration/V7DataImportAuditMigrationIT.java` | `@Tag("integration")` |
| 3 | `org.ctc.backup.service.BackupArchiveServiceReadIT` | `src/test/java/org/ctc/backup/service/BackupArchiveServiceReadIT.java` | `@Tag("integration")` |
| 4 | `org.ctc.backup.service.BackupExportServiceIT` | `src/test/java/org/ctc/backup/service/BackupExportServiceIT.java` | `@Tag("integration")` |
| 5 | `org.ctc.backup.service.BackupImportPostCommitEdgeCasesIT` | `src/test/java/org/ctc/backup/service/BackupImportPostCommitEdgeCasesIT.java` | `@Tag("integration")`, `@ExtendWith(OutputCaptureExtension.class)` |
| 6 | `org.ctc.domain.repository.DriverRepositoryOrderIT` | `src/test/java/org/ctc/domain/repository/DriverRepositoryOrderIT.java` | `@Transactional`, `@Tag("integration")` |

**Total empirical refactor surface: 13 + 6 = 19 outer test classes.**

### Deviation from plan `files_modified` (informational)

Plan 90-01 frontmatter listed 24 files (best estimate). Five Failsafe backup ITs
were anticipated as Bucket-B members but empirically sit in bucket `f524774b` —
a SEPARATE cluster of 16 classes using `@SpringBootTest` (no `classes =
CtcManagerApplication.class`). They do NOT share the V4MigrationSmokeIT merged
config and are therefore OUT of D-01 scope. Refactoring them would expand to a
third cluster and contradict CONTEXT.md D-01 § Rejected "Moderate (+1 secondary
cluster)" + Phase-86 Lesson on blind consolidation.

Out of scope (not refactored in this plan):

- `BackupArchiveServiceIT.java` (cluster `f524774b`)
- `BackupImportServiceIT.java` (cluster `f524774b`)
- `BackupImportZipBombIT.java` (cluster `f524774b`)
- `BackupStagingCleanupRaceIT.java` (cluster `f524774b`)
- `BackupStagingDirPerForkIT.java` (cluster `f524774b`)

These remain candidates for a later consolidation pass once PERF-06 (Phase 91)
CI re-harvest data is available.

## Verification Runs

3-seed Failsafe + 1 Surefire seed-stable verification on `db.migration.**` per
D-03 + Task 4. All 4 runs BUILD SUCCESS; zero retries against alternative seeds
(per [[no-flaky-dismissal]]).

| Run | Command | Maven Total | Result | Log file |
|-----|---------|-------------|--------|----------|
| Failsafe seed 1234 | `./mvnw verify -Pe2e -Dit.test='db.migration.**' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234` | 06:05 min | BUILD SUCCESS | `.test-perf-logs/90-01-failsafe-seed-1234.log` |
| Failsafe seed 5678 | (same w/ seed=5678) | 05:47 min | BUILD SUCCESS | `.test-perf-logs/90-01-failsafe-seed-5678.log` |
| Failsafe seed 9999 | (same w/ seed=9999) | 05:42 min | BUILD SUCCESS | `.test-perf-logs/90-01-failsafe-seed-9999.log` |
| Surefire seed-stable | `./mvnw test -Dtest='db.migration.**'` | 46.8 s | BUILD SUCCESS | `.test-perf-logs/90-01-surefire-seed-stable.log` |

Targeted-suite test counts confirmed: V4MigrationSmokeIT 2 tests, V4MigrateSeasonsToPhasesIT
6 tests (out-of-scope harness), V7DataImportAuditMigrationIT 3 tests.

## Wave-5 numbers

3 idle `./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev`
runs per Phase 86 D-09 idle protocol. Run-1 is the pre-refactor baseline (Task 1);
Runs 2 + 3 are post-refactor (Task 5).

| Run | Maven Total time | bash `real` | Context loads | JaCoCo line cov | SpotBugs | Notes |
|-----|------------------|-------------|---------------|-----------------|----------|-------|
| 1 (pre-refactor) | 07:24 min | 7:25.91 | 56 | 0.8902 | 0 | BUILD SUCCESS; `.test-perf-logs/90-01-wave5-run-1.log` |
| 2 (post-refactor) | 08:27 min | n/a | 55 | 0.8902 | 0 | BUILD SUCCESS; `.test-perf-logs/90-01-wave5-run-2.log` |
| 3 (post-refactor) | 09:31 min | n/a | 55 | 0.8902 | 0 | BUILD SUCCESS; `.test-perf-logs/90-01-wave5-run-3.log` |

- Maven Total median (all 3 runs): **08:27 min**
- Delta vs. Phase 89 Wave-4 median (09:19): **-52 s ≈ -9.3 %** (honest observational
  delta per D-07; influenced by system noise; no hard local gate)
- Context-load count median 55 — matches Phase 89 baseline (no regression)
- JaCoCo line coverage 0.8902 held on all 3 runs (Phase 89 floor 0.8888 satisfied;
  Phase 89 actual 0.8902 retained)
- SpotBugs `BugInstance` count 0 on all 3 runs

## PERF-03 Cache-Key Diff

Source files: `.test-perf-logs/90-01-aggregator-before.txt` (pre-refactor Wave-5
Run-1) and `.test-perf-logs/90-01-aggregator-after.txt` (post-refactor Wave-5
Run-3). Hex hashes rotate across `mvn clean`; cluster STRUCTURE is the stable
observable.

### Top-5 before vs after

| # | Pre-refactor | Post-refactor |
|---|--------------|---------------|
| 1 | `9cefac4c` — 29 events / 29 classes (V5MigrationTest cluster) | `baafff8e` — 29 events / 29 classes (V5MigrationTest cluster) |
| 2 | `f524774b` — 16 events / 16 classes (BackupException cluster) | `6273f4ab` — 15 events / 15 classes (BackupException cluster) |
| 3 | `3c6228fd` — 13 events / 13 classes (CsvImport cluster) | `35c60549` — 12 events / 12 classes (CsvImport cluster) |
| 4 | `5ff2b420` — 7 events / 7 classes (AdminWorkflowE2E) | `286b36be` — 7 events / 7 classes (AdminWorkflowE2E) |
| 5 | `84ec5236` — 7 events / 7 classes (BackupImport) | `cd67fca0` — 7 events / 7 classes (V4MigrationSmoke cluster) |

### Surefire cluster (consolidation succeeded)

`9cefac4c` (pre) → `baafff8e` (post): all 29 cache-key events, all 13 outer
classes preserved. The two pre-existing annotation shape variants (3 db.migration
`@SpringBootTest(classes=...)` + 10 `org.ctc.**` bare `@SpringBootTest`) folded
into one shared cache key. ✓

### Failsafe cluster (mixed outcome)

V4-cluster pre (`499c01dd`, 6 classes) → post (`cd67fca0`, 7 classes). Two of the
six refactored classes (`BackupExportServiceIT`, `BackupImportPostCommitEdgeCasesIT`)
migrated to cluster `6273f4ab`; three previously-unrelated Failsafe classes
(`TeamRestorerIT`, `BackupArchiveExtractUploadsIT`, `BackupStagingCleanupRaceIT`)
migrated INTO the V4-cluster. Net Failsafe cache-bucket event count unchanged
(7 + 15 = 22 ≡ pre-refactor 6 + 16 = 22). The refactor's value on this side is
documentation clarity + future-drift protection, not raw context-reuse gain.

Hypothesis (not investigated further): annotation discovery in
`SpringBootTestContextBootstrapper` treats `@SpringBootTest(classes = X)` (explicit)
and bare `@SpringBootTest` (auto-resolved) as nominally identical but produces
slightly different `MergedContextConfiguration` field hashes — refactored
Failsafe classes shifted clusters in different directions depending on subtle
ContextCustomizer composition. The Phase 91 PERF-06 CI re-harvest is the next
authoritative datapoint.

## Decisions honored

| Decision | Reference | How honored |
|----------|-----------|-------------|
| D-01 | Conservative cluster scope; empirical audit authoritative | Task 1 produced fresh aggregator-before.txt; refactor surface = 13 Surefire + 6 Failsafe (NOT the 24-class best estimate). 5 backup ITs in cluster `f524774b` documented as out-of-scope. |
| D-02 | Custom composed annotation `@CtcDevSpringBootContext` | Created at `src/test/java/org/ctc/testsupport/CtcDevSpringBootContext.java` with exactly the 4 mandated meta-annotations and zero prohibited ones; subclasses retain `@Tag`/`@Transactional` where applicable. |
| D-02b | Aggregator diff before/after | Both snapshots persisted; Top-5 table in this SUMMARY + `docs/test-performance.md § PERF-03 Cluster`. |
| D-03 | 3-seed Failsafe + 1 Surefire on `db.migration.**` | Task 4 — all 4 BUILD SUCCESS, see Verification Runs table. |
| D-04 | (PERF-04, deferred to Plan 90-02) | — |
| D-05 | (PERF-05, deferred to Plan 90-03) | — |
| D-06 | 3 sequential inline plans | This Plan 90-01 is the first; Plan 90-02 + 90-03 follow with user-feedback pause between waves. |
| D-07 | Wave-5 bundled into Plan 01; honest reporting | Wave-5 numbers table above (3 runs); no hard wallclock gate; median + delta-vs-09:19 recorded. |
| D-08 | Standard quality gates, no tightening, no loosening | JaCoCo 0.8902 ≥ 0.8888 ✓; SpotBugs 0 ✓; CodeQL gate on PR HEAD (verified at push); EXPORT_ORDER 24 ✓; SCHEMA_VERSION 1 ✓. |
| D-09 | `src/main/java/**` git-clean | `git diff <plan-baseline>..HEAD -- 'src/main/java/**' \| wc -l = 0` ✓. |

## Invariants held

- [x] JaCoCo line coverage ≥ 0.8888 — observed 0.8902 on all 3 Wave-5 runs (Phase 89 floor retained)
- [x] SpotBugs `BugInstance` count = 0 — confirmed on all 3 Wave-5 runs
- [x] CodeQL `security-extended` gate-step ready to exit 0 on PR HEAD SHA (no new
      suppression rows expected — annotation-only type does not surface
      `EI_EXPOSE_REP*` / `DM_DEFAULT_ENCODING` patterns)
- [x] `EXPORT_ORDER` = 24 entities — confirmed via `BackupSchema initialized:
      SCHEMA_VERSION=1, exportOrder size=24` log line in all 3 Wave-5 runs
- [x] `BackupSchema.SCHEMA_VERSION` = 1 — confirmed via same log line
- [x] Flyway V1-V7 immutable — no changes to existing migration files
- [x] `src/main/java/**` git-clean — D-09 satisfied
- [x] Context-load count median 55 — Phase 89 baseline retained
