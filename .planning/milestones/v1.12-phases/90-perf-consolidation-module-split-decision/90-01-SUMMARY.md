---
phase: 90-perf-consolidation-module-split-decision
plan: 01
slug: perf-03-cluster-consolidation
status: in-progress
wave: 1
depends_on: []
requirements: [PERF-03]
---

## Objective recap

PERF-03 consolidates the Top-1 + Top-4 cache-key buckets identified by Phase 89's PERF-02 instrumentation onto a shared composed annotation `@CtcDevSpringBootContext`. The merged `MergedContextConfiguration` collapses two distinct cache buckets — `9cefac4c` (Surefire) + `499c01dd` (Failsafe, V4MigrationSmokeIT) — into a single shared bucket. Wave-5 idle 3-run measurement bundled per D-07; honest observational reporting per D-07/D-02 (no hard local wallclock gate). Authoritative CI re-harvest stays deferred to PERF-06 (Phase 91).

## What shipped

(Filled by Task 6.)

## Hash-bucket Audit

Source: `.test-perf-logs/90-01-aggregator-before.txt` (Wave-5 Run-1 `./mvnw clean verify -Pe2e` BUILD SUCCESS, Maven Total 07:24 min, 5 sidecar files, 131 cache-key events). Per D-01 mandate: this empirical enumeration — not the 24-class PATTERNS.md best estimate — is the authoritative refactor surface for Task 3.

**Hash rotation observed:** Phase 89's `f524774b` (V4MigrationSmokeIT cluster) maps to `499c01dd` in this run. Per CONTEXT.md D-01 + RESEARCH.md §Open Questions Q1, Java identity-hashCode randomization rotates hex values across `mvn clean` invocations; the CLUSTER STRUCTURE (which classes collide) is stable. Phase 89's `9cefac4c` (V5MigrationTest cluster) coincidentally retained its hex.

### Bucket A — `9cefac4c` (Surefire `@SpringBootTest(classes = CtcManagerApplication.class) + @ActiveProfiles("dev")`)

29 cache-key events across 29 reported classes; 13 outer test classes after collapsing the 15 `@Nested` PlayoffServiceTest inner classes (which inherit per Spring TCF + JUnit composed-annotation inheritance — RESEARCH.md Pitfall 4 + `.planning/codebase/TESTING.md` §`@Nested` Inheritance).

Outer classes to annotate (13):

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
| 12 | `org.ctc.domain.service.PlayoffServiceTest` (outer; 15 `@Nested` inner classes inherit) | `src/test/java/org/ctc/domain/service/PlayoffServiceTest.java` |
| 13 | `org.ctc.domain.service.SwissPairingServiceTest` | `src/test/java/org/ctc/domain/service/SwissPairingServiceTest.java` |

### Bucket B — `499c01dd` (Failsafe `@SpringBootTest(classes = CtcManagerApplication.class) + @ActiveProfiles("dev")` + per-class `@Tag("integration")`, optional `@Transactional`)

6 cache-key events across 6 classes; 6 outer test classes (no `@Nested` collapse needed).

| # | FQN | Source path | Extra annotations to KEEP on subclass |
|---|-----|-------------|---------------------------------------|
| 1 | `db.migration.V4MigrationSmokeIT` | `src/test/java/db/migration/V4MigrationSmokeIT.java` | `@Transactional`, `@Tag("integration")` |
| 2 | `db.migration.V7DataImportAuditMigrationIT` | `src/test/java/db/migration/V7DataImportAuditMigrationIT.java` | `@Tag("integration")` |
| 3 | `org.ctc.backup.service.BackupArchiveServiceReadIT` | `src/test/java/org/ctc/backup/service/BackupArchiveServiceReadIT.java` | `@Tag("integration")` |
| 4 | `org.ctc.backup.service.BackupExportServiceIT` | `src/test/java/org/ctc/backup/service/BackupExportServiceIT.java` | `@Tag("integration")` |
| 5 | `org.ctc.backup.service.BackupImportPostCommitEdgeCasesIT` | `src/test/java/org/ctc/backup/service/BackupImportPostCommitEdgeCasesIT.java` | `@Tag("integration")` |
| 6 | `org.ctc.domain.repository.DriverRepositoryOrderIT` | `src/test/java/org/ctc/domain/repository/DriverRepositoryOrderIT.java` | `@Tag("integration")` |

**Total empirical refactor surface: 13 + 6 = 19 outer test classes.**

### Deviation from plan `files_modified` (informational)

Plan 90-01 frontmatter listed 24 files (best estimate). Five Failsafe backup ITs were anticipated as Bucket-B members but empirically sit in bucket `f524774b` — a SEPARATE cluster of 16 classes using `@SpringBootTest` (no `classes = CtcManagerApplication.class`). They do NOT share the V4MigrationSmokeIT merged-config and are therefore OUT of D-01 scope (which targets only the V5+V4 cluster pair). Refactoring them would expand to a third cluster and contradict CONTEXT.md D-01 § Rejected "Moderate (+1 secondary cluster)" + Phase-86 Lesson on blind consolidation.

Out of scope (not refactored in this plan):
- `BackupArchiveServiceIT.java` (cluster `f524774b`)
- `BackupImportServiceIT.java` (cluster `f524774b`)
- `BackupImportZipBombIT.java` (cluster `f524774b`)
- `BackupStagingCleanupRaceIT.java` (cluster `f524774b`)
- `BackupStagingDirPerForkIT.java` (cluster `f524774b`)

These remain candidates for a later consolidation pass once PERF-06 (Phase 91) CI re-harvest data is available.

## Verification Runs

(Filled by Task 4.)

## Wave-5 numbers

| Run | Maven Total time | bash `real` | Context loads | JaCoCo | Notes |
|-----|------------------|-------------|---------------|--------|-------|
| 1 (pre-refactor baseline) | 07:24 min | 7:25.91 | 56 | 0.8902 | BUILD SUCCESS; `.test-perf-logs/90-01-wave5-run-1.log` |
| 2 (post-refactor) | _filled by Task 5_ | _filled_ | _filled_ | _filled_ | _filled_ |
| 3 (post-refactor) | _filled by Task 5_ | _filled_ | _filled_ | _filled_ | _filled_ |

Phase 89 Wave-4 baseline: 09:19 Maven median / 55 context loads / 0.8902 JaCoCo.

## PERF-03 Cache-Key Diff

(Filled by Task 5 from `.test-perf-logs/90-01-aggregator-{before,after}.txt`.)

## Decisions honored

(Filled by Task 6 — D-01..D-09 row-by-row.)

## Invariants held

(Filled by Task 6 — JaCoCo / SpotBugs / CodeQL / EXPORT_ORDER / SCHEMA_VERSION / Flyway / git-clean.)
