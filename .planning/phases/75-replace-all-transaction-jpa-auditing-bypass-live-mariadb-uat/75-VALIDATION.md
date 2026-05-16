---
phase: 75
slug: replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-14
---

# Phase 75 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from `75-RESEARCH.md` §"Validation Architecture" (Nyquist 8 dimensions).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot 4.0.6 Test + Mockito + Playwright (E2E) + AssertJ |
| **Config file** | `pom.xml` (Surefire L184-194; Failsafe L256-278; JaCoCo L270-319) |
| **Quick run command** | `./mvnw -q -Dtest='BackupImport*Test,EntityRestorer*Test,DataImportAuditServiceTest,*RestorerTest' test` |
| **Full suite command** | `./mvnw verify` (Unit + IT + JaCoCo) |
| **E2E suite command** | `./mvnw verify -Pe2e` |
| **MariaDB smoke command** | `./mvnw verify -Dit.test=BackupImportMariaDbSmokeIT -Dspring-boot.run.profiles=local` (or via `mariadb-migration-smoke.yml` workflow on CI) |
| **Estimated runtime** | ~90 s (`verify`), ~3 min (`verify -Pe2e`), +90 s for MariaDB smoke on CI |

---

## Sampling Rate

- **After every task commit:** Run quick test selector: `./mvnw -q -Dtest='<JustWrittenTest>' test` (~10 s)
- **After every plan wave:** Run `./mvnw verify` (Unit + IT + JaCoCo; ~90 s)
- **Before `/gsd-verify-work`:** Full `./mvnw verify -Pe2e` BUILD SUCCESS + `mariadb-migration-smoke.yml` CI green
- **Max feedback latency:** ~60 s unit · ~90 s IT · ~3 min E2E · ~2 min MariaDB smoke (CI)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 75-01-01 | 01 | 0 | IMPORT-05 | — | n/a (infrastructure) | unit | `./mvnw -Dtest='EntityRestorerTest' test` | ❌ W0 | ⬜ pending |
| 75-02-01 | 02 | 0 | IMPORT-05 | T-IMPORT-RESTORE-FAILURE-INJECTOR | `NoopRestoreFailureInjector @Primary` blocks production code path | unit | `./mvnw -Dtest='NoopRestoreFailureInjectorTest' test` | ❌ W0 | ⬜ pending |
| 75-03-01 | 03 | 1 | IMPORT-07 | T-IMPORT-AUDIT-TAMPER | `executedBy` is recorded across rollback via REQUIRES_NEW | unit | `./mvnw -Dtest='DataImportAuditServiceTest' test` | ❌ W0 | ⬜ pending |
| 75-03-02 | 03 | 1 | IMPORT-07 | T-IMPORT-AUDIT-JSON-INJECTION | `tableCountsWiped`/`tableCountsRestored` round-trip via `backupObjectMapper` (FAIL_ON_UNKNOWN_PROPERTIES) | unit | `./mvnw -Dtest='DataImportAuditSerializationTest' test` | ❌ W0 | ⬜ pending |
| 75-04-01 | 04 | 1 | IMPORT-05 | T-IMPORT-SQLI | SQL is hard-coded `INSERT ... VALUES (?,...)`; values bound via `PreparedStatement` | unit | `./mvnw -Dtest='*RestorerTest' test` | ❌ W0 (24 tests) | ⬜ pending |
| 75-04-02 | 04 | 1 | IMPORT-05 | — | 2-pass NULL-then-UPDATE preserves self-FK without disabling constraints | unit | `./mvnw -Dtest='TeamRestorerTest' test` | ❌ W0 | ⬜ pending |
| 75-05-01 | 05 | 2 | IMPORT-05 | T-IMPORT-ZIP-SLIP / T-IMPORT-ZIP-BOMB | `PathTraversalGuard` per-entry + `LimitedInputStream` cap reused from Phase 74 | IT | `./mvnw -Dit.test='BackupArchiveExtractUploadsIT' verify` | ❌ W0 | ⬜ pending |
| 75-06-01 | 06 | 2 | IMPORT-05 | T-IMPORT-L1-CACHE-STALE | `em.flush() + em.clear()` after native DELETE | IT | `./mvnw -Dit.test='BackupImportExecuteIT' verify` | ❌ W0 | ⬜ pending |
| 75-06-02 | 06 | 2 | IMPORT-06 | T-IMPORT-AUDIT-ROLLBACK | `success=false` audit row survives wipe-rollback via REQUIRES_NEW | IT | `./mvnw -Dit.test='BackupImportRollbackIT' verify` | ❌ W0 | ⬜ pending |
| 75-07-01 | 07 | 3 | IMPORT-07 | T-IMPORT-ATOMIC-MOVE-FAILURE | `Files.move(..., ATOMIC_MOVE)` triple with Step-1 best-effort revert on Step-2 failure | IT | `./mvnw -Dit.test='BackupImportPostCommitIT' verify` | ❌ W0 | ⬜ pending |
| 75-07-02 | 07 | 3 | IMPORT-06 | — | Two-pass `TeamRestorer` against H2 (real `JdbcTemplate`) | IT | `./mvnw -Dit.test='TeamRestorerIT' verify` | ❌ W0 | ⬜ pending |
| 75-08-01 | 08 | 3 | IMPORT-05 | T-IMPORT-CSRF (carry-forward Phase 74) | `POST /admin/backup/import-execute` upgraded to real execute; CSRF + profile-auth unchanged | E2E | `./mvnw verify -Pe2e -Dit.test='BackupImportE2ETest'` | ✓ extend Phase 74 file | ⬜ pending |
| 75-09-01 | 09 | 4 | QUAL-03 | — | Saison-2023 round-trip on Testcontainers MariaDB; per-entity row counts equal pre-export | IT | `./mvnw verify -Dit.test='BackupImportMariaDbSmokeIT' -Dspring-boot.run.profiles=local` | ❌ W0 | ⬜ pending |
| 75-10-01 | 10 | 4 | QUAL-03 | — | Operator captures 6 screenshot pairs on Saison-2023 fixture (local MariaDB) | HUMAN-UAT | `gsd-auto-uat` skill or `playwright-cli open http://localhost:9091/...` (memory `feedback_playwright_cli.md`) | ❌ W0 (doc + screenshots) | ⬜ pending |
| 75-coverage | n/a | n/a | (coverage) | — | JaCoCo line coverage ≥ 82% held across +30 new Java classes | Build gate | `./mvnw verify` (jacoco:check phase, pom.xml:312) | ✓ gate exists | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

> Task IDs are placeholders — final IDs are assigned by the planner during plan drafting. Each PLAN.md task's `<automated>` block must reference an entry from this table.

---

## Wave 0 Requirements

Production scaffolding (interfaces + no-op + property keys + JDBC URL fix):

- [ ] `src/main/java/org/ctc/backup/restore/EntityRestorer.java` — interface (D-05)
- [ ] `src/main/java/org/ctc/backup/restore/RestoreFailureInjector.java` — interface (D-13)
- [ ] `src/main/java/org/ctc/backup/restore/NoopRestoreFailureInjector.java` — `@Primary` no-op
- [ ] `src/main/java/org/ctc/backup/exception/UploadsRestoreException.java`
- [ ] `src/main/java/org/ctc/backup/exception/RestoreFailureSimulatedException.java`
- [ ] `src/main/resources/application.yml` — add `app.backup.import-backups-dir: data/.import-backups`
- [ ] `src/main/resources/application-local.yml` — append `?rewriteBatchedStatements=true` to JDBC URL (RESEARCH §10)
- [ ] `src/main/resources/application-docker.yml` — append `?rewriteBatchedStatements=true` to JDBC URL (RESEARCH §10)
- [ ] `src/main/resources/application-prod.yml` — append `&rewriteBatchedStatements=true` to JDBC URL template (RESEARCH §10)

Test scaffolds (one per Per-Task entry):

- [ ] `src/test/java/org/ctc/backup/restore/entity/*RestorerTest.java` × 24 — Surefire unit tests (insertSql shape + setter coercion)
- [ ] `src/test/java/org/ctc/backup/restore/entity/TeamRestorerIT.java` — 2-pass restore against H2
- [ ] `src/test/java/org/ctc/backup/restore/NoopRestoreFailureInjectorTest.java` — @Primary no-op contract
- [ ] `src/test/java/org/ctc/backup/audit/DataImportAuditServiceTest.java` — REQUIRES_NEW via `@MockitoSpyBean` on `PlatformTransactionManager`
- [ ] `src/test/java/org/ctc/backup/audit/DataImportAuditSerializationTest.java` — LONGTEXT JSON round-trip
- [ ] `src/test/java/org/ctc/backup/service/BackupImportExecuteIT.java` — IMPORT-05 happy path on H2
- [ ] `src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java` — D-13 failure injection on H2
- [ ] `src/test/java/org/ctc/backup/service/BackupImportPostCommitIT.java` — IMPORT-06 atomic-move triple
- [ ] `src/test/java/org/ctc/backup/service/BackupArchiveExtractUploadsIT.java` — `PathTraversalGuard` + size cap on uploads-tree extract
- [ ] `src/test/java/org/ctc/backup/service/BackupImportMariaDbSmokeIT.java` — QUAL-03 CI layer (Testcontainers MariaDB, @ActiveProfiles("local"))
- [ ] `src/test/java/org/ctc/e2e/BackupImportE2ETest.java` — EXTEND existing Phase 74 file with one new `@Test` covering real success-flash flow

Documentation + manual-UAT artifacts:

- [ ] `.planning/phases/75-.../75-HUMAN-UAT.md` — D-16 six-screenshot checklist (Standings R/A, R/B, Driver Ranking, PLAYOFF bracket, sub-team Phase Breakdown, driver Phase Breakdown)
- [ ] `.screenshots/75/before/` directory (operator-populated)
- [ ] `.screenshots/75/after/` directory (operator-populated)

*Wave 0 is satisfied when every checkbox above is ticked AND `./mvnw verify` is green on the Wave 0 commit.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Visual identity of `/seasons/2023` Standings (Phase=REGULAR, Group=A and B) post-import | QUAL-03 | Pixel-level visual diff requires human judgment; standings rendering is OSIV-driven Thymeleaf | `./mvnw spring-boot:run -Dspring-boot.run.profiles=local,demo` → `gsd-auto-uat` or `playwright-cli open http://localhost:9091/seasons/2023` (Desktop + Mobile) before+after import; save pairs to `.screenshots/75/{before,after}/standings-{a,b}-{desktop,mobile}.png` |
| Visual identity of `/seasons/2023` Driver Ranking (default phase) post-import | QUAL-03 | Same — visual sight-check on rendered table | `playwright-cli open http://localhost:9091/seasons/2023/drivers` (Desktop + Mobile); pairs in `.screenshots/75/{before,after}/driver-ranking-{desktop,mobile}.png` |
| Visual identity of `/seasons/2023/playoff` PLAYOFF bracket post-import | QUAL-03 | Bracket graphic is generated server-side; positions matter | `playwright-cli open http://localhost:9091/seasons/2023/playoff` (Desktop + Mobile); pairs in `.screenshots/75/{before,after}/playoff-{desktop,mobile}.png` |
| Visual identity of `/teams/<sub-team>` Phase Breakdown (Saison-2023 sub-team) | QUAL-03 | Sub-team rendering depends on `RaceLineup`-as-source-of-truth (memory `feedback_racelineup_source_of_truth.md`) | `playwright-cli open http://localhost:9091/teams/<slug>` (operator picks any sub-team with multi-phase data); pair in `.screenshots/75/{before,after}/team-breakdown.png` |
| Visual identity of `/drivers/<top-driver>` Phase Breakdown (multi-phase results) | QUAL-03 | Driver phase breakdown depends on aggregated `RaceLineup` rows | `playwright-cli open http://localhost:9091/drivers/<slug>` (operator picks any Saison-2023 driver with multi-phase results); pair in `.screenshots/75/{before,after}/driver-breakdown.png` |
| Operator confirms `data/.import-backups/<ts>/uploads-old/` exists 24 h after successful import | IMPORT-07 | File-system retention check; relies on operator not running cleanup | Manual `ls -la data/.import-backups/`; record in `75-HUMAN-UAT.md` |

> All other Phase 75 behaviors have automated verification.

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify references mapped to the Per-Task table above
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all `❌ W0` references in the Per-Task table
- [ ] No `--watch` / continuous-mode flags (carry-forward Phase 74 discipline)
- [ ] Feedback latency < ~3 min including E2E
- [ ] JaCoCo line coverage ≥ 82% gate green on phase exit (`./mvnw verify`)
- [ ] `mariadb-migration-smoke.yml` CI workflow green
- [ ] `75-HUMAN-UAT.md` operator sign-off recorded (PASS/FAIL + free-text)
- [ ] `nyquist_compliant: true` set in this frontmatter after sign-off

**Approval:** pending
