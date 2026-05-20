---
status: complete
phase: 90-perf-consolidation-module-split-decision
source: [90-01-SUMMARY.md, 90-02-SUMMARY.md, 90-03-SUMMARY.md]
started: 2026-05-20T07:59:29Z
updated: 2026-05-20T08:03:00Z
---

## Current Test

[testing complete]

## Tests

### 1. PERF-03 — Composed annotation & cluster consolidation
expected: |
  `src/test/java/org/ctc/testsupport/CtcDevSpringBootContext.java` exists
  with exactly 4 meta-annotations (`@Target`, `@Retention`,
  `@SpringBootTest(classes = CtcManagerApplication.class)`, `@ActiveProfiles("dev")`)
  and Javadoc warning against `@DirtiesContext` / `@Tag` / `@Transactional` /
  `@DynamicPropertySource`. 19 outer test classes (13 Surefire + 6 Failsafe)
  use the composed annotation. `docs/test-performance.md § PERF-03 Cluster
  Consolidation (Phase 90 Plan 01)` exists with before/after Top-5 table and
  Wave-5 measurement table. Surefire cluster `9cefac4c` (pre) → `baafff8e`
  (post) collapsed 2 annotation-shape variants into 1 shared cache key (13
  outer classes preserved).
result: pass

### 2. PERF-03 — 3-seed Failsafe + Surefire verification on `db.migration.**`
expected: |
  `db.migration.**` test surface passed 4 BUILD SUCCESS runs without flaky
  retries: Failsafe seeds 1234 / 5678 / 9999 plus 1 Surefire seed-stable run.
  Log files exist under `.test-perf-logs/90-01-failsafe-seed-{1234,5678,9999}.log`
  and `.test-perf-logs/90-01-surefire-seed-stable.log`. Targeted test counts:
  V4MigrationSmokeIT 2 tests, V7DataImportAuditMigrationIT 3 tests.
result: pass

### 3. PERF-04 — Testcontainers `.withReuse(true)` wired on both MariaDB ITs
expected: |
  Both `src/test/java/org/ctc/backup/service/BackupImportMariaDbSmokeIT.java`
  and `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` (nested
  `MariaDbRoundTripTests`) MariaDBContainer chains end with `.withReuse(true)`,
  with chain order `.withDatabaseName / .withUsername / .withPassword`
  preserved (Testcontainers #2515 hash-stability invariant). Both ITs remain
  gated by `@EnabledIfSystemProperty(named = "docker.available", matches =
  "true")` so CI behavior is unchanged (CI never sets the flag).
result: pass

### 4. PERF-04 — Reuse opt-in documented in README + docs/test-performance.md
expected: |
  `docs/test-performance.md § PERF-04 Testcontainers Reuse` section exists
  after § PERF-03, containing all 5 required elements: opt-in prose
  (`~/.testcontainers.properties` per-developer file with
  `testcontainers.reuse.enable=true`), `docker ps` verification command,
  zero-CI-impact framing, T-90-TC-01 defensive-seed-in-`@BeforeEach`
  paragraph, T-90-TC-02 `docker container prune` cleanup hint. `README.md §
  Test Performance` includes one additional sentence pointing at the new
  docs section; single `## Test Performance` H2 preserved.
result: pass

### 5. PERF-05 — Test-module-split decision recorded with `defer` verdict
expected: |
  `docs/test-performance.md § Test-Module-Split Decision` section exists
  exactly once at the end of the file with all 6 required structural elements:
  (1) verdict line `**Verdict (v1.12):** Defer — re-evaluate in v1.13 against
  PERF-06 CI re-harvest baseline.`, (2) Blocker 1 TestDataService cross-boundary
  with `@Profile({"dev","local"})` anchor at
  `src/main/java/org/ctc/admin/TestDataService.java`, (3) Blocker 2 IDE-friction
  risk citing v1.11 JDT-cache pathology + `[[clean-maven-build-authority]]`,
  (4) Blocker 3 absent hard cumulative-effect data, (5) v1.13 re-evaluation
  trigger paragraph naming the PERF-06 CI median condition, (6) `Why not
  reject?` paragraph citing Phase 86 D-15 OR-branch precedent.
result: pass

### 6. Quality gates held across all 3 plans
expected: |
  JaCoCo line coverage 0.8902 observed on all Wave-5 runs (≥ 0.8888 Phase 89
  floor); SpotBugs `BugInstance` count 0 on every verify; `BackupSchema
  initialized: SCHEMA_VERSION=1, exportOrder size=24` log line confirms wire
  contracts unchanged; Flyway V1-V7 immutable. `src/main/java/**` git-clean
  across all 3 plans (D-09 invariant) — `git diff <phase-baseline>..HEAD --
  'src/main/java/**' | wc -l = 0`.
result: pass

### 7. Phase-gate `./mvnw verify -Pe2e` BUILD SUCCESS on Plan 03 HEAD
expected: |
  Final phase-gate `./mvnw verify -Pe2e --no-transfer-progress
  -Dspring.profiles.active=dev` returned BUILD SUCCESS at Maven Total 07:36 min
  on Plan 90-03's tip commit (`36e6a70a docs(90-03): plan SUMMARY + Phase 90
  closure pointer`). Log persisted at `.test-perf-logs/90-03-verify.log`.
result: pass

## Summary

total: 7
passed: 7
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

[none yet]
