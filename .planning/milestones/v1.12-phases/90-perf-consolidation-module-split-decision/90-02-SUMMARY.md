---
phase: 90-perf-consolidation-module-split-decision
plan: 02
slug: perf-04-testcontainers-reuse
status: complete
completed: 2026-05-20
wave: 2
depends_on: [90-01]
requirements: [PERF-04]
---

## Objective recap

PERF-04 appends `.withReuse(true)` to both existing `MariaDBContainer<>`
declarations and documents the per-developer `~/.testcontainers.properties`
opt-in surface. CI behavior unchanged — both ITs remain gated by
`@EnabledIfSystemProperty(named = "docker.available", matches = "true")` and CI
never sets the flag (D-04 invariant + Phase 77 D-05).

## What shipped

- `src/test/java/org/ctc/backup/service/BackupImportMariaDbSmokeIT.java`
  MariaDBContainer chain now ends with `.withReuse(true)` (line 108). Chain
  order `.withDatabaseName / .withUsername / .withPassword` preserved
  (Testcontainers issue #2515 hash-stability invariant).
- `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` nested
  `MariaDbRoundTripTests` MariaDBContainer chain now ends with
  `.withReuse(true)` (line 522). Same chain-order preservation.
- `docs/test-performance.md § PERF-04 Testcontainers Reuse` new level-2
  section appended after § PERF-03 with the five required elements:
  opt-in mechanism prose, `docker ps` verification, zero-CI-impact framing,
  T-90-TC-01 defensive seed-in-BeforeEach note, T-90-TC-02 `docker container
  prune` cleanup hint.
- `README.md § Test Performance` extended with one additional sentence
  pointing at the new docs section. Single `## Test Performance` level-2
  header preserved.

## Decisions honored

| Decision | Reference | How honored |
|----------|-----------|-------------|
| D-04 | Testcontainers reuse opt-in pattern | `.withReuse(true)` appended to both existing MariaDB chains; `~/.testcontainers.properties` documented as the per-developer opt-in; CI invariant preserved via existing `@EnabledIfSystemProperty` gates. |
| D-04b | Production `application*.yml` + `BackupStagingCleanup.java` untouched | `git diff <plan-baseline>..HEAD -- 'src/main/resources/application*.yml' 'src/main/java/org/ctc/backup/service/BackupStagingCleanup.java' \| wc -l = 0`. |
| D-08 | Standard quality gates, no tightening, no loosening | Phase-gate `./mvnw verify -Pe2e` BUILD SUCCESS; JaCoCo 0.8902 ≥ 0.8888 ✓; SpotBugs 0 ✓; CodeQL gate ready to exit 0 on PR HEAD (no new suppressions). |
| D-09 | `src/main/java/**` git-clean | Plan 90-02 only touched `src/test/java/**` + `docs/` + `README.md`. `git diff 82ef2ecf..HEAD -- 'src/main/java/**' \| wc -l = 0`. |

## Threat-Model Mitigations

| Threat ID | Category | Mitigation Evidence |
|-----------|----------|---------------------|
| T-90-TC-01 | Information Disclosure (low — local-dev only): reuse persists DB state between consecutive `./mvnw verify` runs; future MariaDB IT could observe stale rows if not defensively seeded | `docs/test-performance.md § PERF-04 § Future MariaDB IT authors — seed defensively` paragraph requires `testDataService.seed(...)` (or equivalent) in `@BeforeEach`. Both existing ITs already comply: `BackupImportMariaDbSmokeIT` replaces the entire fixture in `@BeforeEach`; `BackupRoundTripIT.MariaDbRoundTripTests` wipes and restores the DB inside the test body. Production data never exposed (test-only `ctc_test` schema; `ctc/test` credentials). Residual severity: Low. |
| T-90-TC-02 | Denial of Service (local dev only): orphan reuse-mode MariaDB containers accumulate on developer machine across project rotations (Ryuk disabled in reuse mode) | `docs/test-performance.md § PERF-04 § Cleanup hint` paragraph documents `docker container prune --filter "label=org.testcontainers.reuse.enable=true"`. Diagnostic via `docker ps --filter label=org.testcontainers.reuse.enable=true`. Recovery is one shell command; disk pressure is the only impact. Residual severity: Low. |

## Invariants held

- [x] JaCoCo line coverage ≥ 0.8888 — observed 0.8902 on Plan-02 verify (Phase 89 baseline retained)
- [x] SpotBugs `BugInstance` count = 0 — confirmed via `[INFO] BugInstance size is 0` in `.test-perf-logs/90-02-verify.log`
- [x] CodeQL `security-extended` gate-step ready to exit 0 on PR HEAD SHA (no new suppression rows expected — `.withReuse(true)` does not surface CodeQL-relevant patterns)
- [x] `EXPORT_ORDER` = 24 entities — confirmed via `BackupSchema initialized: SCHEMA_VERSION=1, exportOrder size=24` log line (unchanged from Plan 90-01)
- [x] `BackupSchema.SCHEMA_VERSION` = 1 — confirmed via same log line
- [x] Flyway V1-V7 immutable — no changes to existing migration files
- [x] `src/main/java/**` git-clean across Plan 90-02 (D-09)
- [x] `src/main/resources/application*.yml` git-clean across Plan 90-02 (D-04b)
- [x] `src/main/java/org/ctc/backup/service/BackupStagingCleanup.java` git-clean across Plan 90-02 (D-04b)
- [x] Both MariaDB ITs still gated by `@EnabledIfSystemProperty(named = "docker.available", matches = "true")` (verified via grep — 3 occurrences in BackupImportMariaDbSmokeIT, 5 in BackupRoundTripIT counting Javadoc references)
- [x] Testcontainers chain order preserved on both files (`.withDatabaseName / .withUsername / .withPassword` unchanged; `.withReuse(true)` appended at end — Testcontainers issue #2515 hash-stability invariant)

## Verification log

- Phase-gate `./mvnw verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev`:
  BUILD SUCCESS, Maven Total 07:35 min, log `.test-perf-logs/90-02-verify.log`.
- Both MariaDB ITs SKIP on this verify (no `-Ddocker.available=true`); the
  verify confirms only that `.withReuse(true)` compiles cleanly and nothing
  else regressed.
- Manual developer-mode verification (post-merge, out of scope for the
  automated gate per VALIDATION.md §Manual-Only Verifications): set
  `~/.testcontainers.properties` to `testcontainers.reuse.enable=true`, run
  `docker.available=true ./mvnw -q verify -Pe2e
  -Dit.test='org.ctc.backup.service.BackupImportMariaDbSmokeIT'` twice; expect
  `docker ps --filter label=org.testcontainers.reuse.enable=true` to list the
  same container ID across runs and Testcontainers to log `Reusing
  container ...` on the second invocation.
