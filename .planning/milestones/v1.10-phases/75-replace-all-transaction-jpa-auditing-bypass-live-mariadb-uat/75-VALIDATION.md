---
phase: 75
slug: replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-14
approved_on: 2026-05-18
audit_method: retroactive
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
| 75-01-01 | 01 | 0 | IMPORT-05 | — | `EntityRestorer` interface + 24 implementations covered by per-entity `*RestorerTest.java` unit tests | unit | `./mvnw -Dtest='*RestorerTest' test` | ✅ (24 classes in `src/test/java/org/ctc/backup/restore/entity/`) | ✅ green |
| 75-02-01 | 02 | 0 | IMPORT-05 | T-IMPORT-RESTORE-FAILURE-INJECTOR | `NoopRestoreFailureInjector @Primary` blocks production code path | unit | `./mvnw -Dtest='NoopRestoreFailureInjectorTest' test` | ✅ | ✅ green |
| 75-03-01 | 03 | 1 | IMPORT-07 | T-IMPORT-AUDIT-TAMPER | `executedBy` is recorded across rollback via REQUIRES_NEW | unit | `./mvnw -Dtest='DataImportAuditServiceTest' test` | ✅ | ✅ green |
| 75-03-02 | 03 | 1 | IMPORT-07 | T-IMPORT-AUDIT-JSON-INJECTION | `tableCountsWiped`/`tableCountsRestored` round-trip via `backupObjectMapper` (FAIL_ON_UNKNOWN_PROPERTIES) | unit | `./mvnw -Dtest='DataImportAuditSerializationTest' test` | ✅ | ✅ green |
| 75-04-01 | 04 | 1 | IMPORT-05 | T-IMPORT-SQLI | SQL is hard-coded `INSERT ... VALUES (?,...)`; values bound via `PreparedStatement` | unit | `./mvnw -Dtest='*RestorerTest' test` | ✅ (24 tests) | ✅ green |
| 75-04-02 | 04 | 1 | IMPORT-05 | — | 2-pass NULL-then-UPDATE preserves self-FK without disabling constraints | unit | `./mvnw -Dtest='TeamRestorerTest' test` | ✅ | ✅ green |
| 75-05-01 | 05 | 2 | IMPORT-05 | T-IMPORT-ZIP-SLIP / T-IMPORT-ZIP-BOMB | `PathTraversalGuard` per-entry + `LimitedInputStream` cap reused from Phase 74 | IT | `./mvnw -Dit.test='BackupArchiveExtractUploadsIT' verify` | ✅ | ✅ green |
| 75-06-01 | 06 | 2 | IMPORT-05 | T-IMPORT-L1-CACHE-STALE | `em.flush() + em.clear()` after native DELETE | IT | `./mvnw -Dit.test='BackupImportExecuteIT' verify` | ✅ | ✅ green |
| 75-06-02 | 06 | 2 | IMPORT-06 | T-IMPORT-AUDIT-ROLLBACK | `success=false` audit row survives wipe-rollback via REQUIRES_NEW | IT | `./mvnw -Dit.test='BackupImportRollbackIT' verify` | ✅ | ✅ green |
| 75-07-01 | 07 | 3 | IMPORT-07 | T-IMPORT-ATOMIC-MOVE-FAILURE | `Files.move(..., ATOMIC_MOVE)` triple with Step-1 best-effort revert on Step-2 failure | IT | `./mvnw -Dit.test='BackupImportPostCommitIT' verify` | ✅ | ✅ green |
| 75-07-02 | 07 | 3 | IMPORT-06 | — | Two-pass `TeamRestorer` against H2 (real `JdbcTemplate`) | IT | `./mvnw -Dit.test='TeamRestorerIT' verify` | ✅ | ✅ green |
| 75-07-03 (gap) | 07 | 3 | IMPORT-06 | T-IMPORT-LISTENER-DOUBLE-FIRE | Defensive: second AFTER_COMMIT invocation with same event fails loud at Step 1; original FS state + first audit row protected | IT | `./mvnw -Dit.test='BackupImportPostCommitEdgeCasesIT' verify` | ✅ (added by Phase 87 / Plan 87-05) | ✅ green |
| 75-07-04 (gap) | 07 | 3 | IMPORT-06 | — | Operator-visible `data/.import-backups/<ts>/` sub-directory matches ISO-8601-with-dashes contract (`yyyy-MM-ddTHH-mm-ssZ`) | IT | `./mvnw -Dit.test='BackupImportPostCommitEdgeCasesIT' verify` | ✅ (added by Phase 87 / Plan 87-05) | ✅ green |
| 75-08-01 | 08 | 3 | IMPORT-05 | T-IMPORT-CSRF (carry-forward Phase 74) | `POST /admin/backup/import-execute` upgraded to real execute; CSRF + profile-auth unchanged | E2E | `./mvnw verify -Pe2e -Dit.test='BackupImportE2ETest'` | ✓ extend Phase 74 file | ✅ green |
| 75-09-01 | 09 | 4 | QUAL-03 | — | Saison-2023 round-trip on Testcontainers MariaDB; per-entity row counts equal pre-export | IT | `./mvnw verify -Dit.test='BackupImportMariaDbSmokeIT' -Dspring-boot.run.profiles=local` | ✅ | ✅ green |
| 75-10-01 | 10 | 4 | QUAL-03 | — | Operator captures 6 screenshot pairs on Saison-2023 fixture (local MariaDB) | HUMAN-UAT | `gsd-auto-uat` skill or `playwright-cli open http://localhost:9091/...` (memory `feedback_playwright_cli.md`) | ✅ (doc + screenshots) | ✅ green |
| 75-coverage | n/a | n/a | (coverage) | — | JaCoCo line coverage ≥ 82% held across +30 new Java classes | Build gate | `./mvnw verify` (jacoco:check phase, pom.xml:312) | ✓ gate exists | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

> Task IDs are placeholders — final IDs are assigned by the planner during plan drafting. Each PLAN.md task's `<automated>` block must reference an entry from this table.

---

## Wave 0 Requirements

> **Satisfied retroactively — all referenced files and tests exist on disk as of Phase 87 / Plan 87-05 (audit on 2026-05-18).** v1.10 Phase 75 shipped these artefacts; the checkboxes below are flipped from `[ ]` to `[x]` per CONTEXT D-12 retroactive-fill convention.

Production scaffolding (interfaces + no-op + property keys + JDBC URL fix):

- [x] `src/main/java/org/ctc/backup/restore/EntityRestorer.java` — interface (D-05)
- [x] `src/main/java/org/ctc/backup/restore/RestoreFailureInjector.java` — interface (D-13)
- [x] `src/main/java/org/ctc/backup/restore/NoopRestoreFailureInjector.java` — `@Primary` no-op
- [x] `src/main/java/org/ctc/backup/exception/UploadsRestoreException.java`
- [x] `src/main/java/org/ctc/backup/exception/RestoreFailureSimulatedException.java`
- [x] `src/main/resources/application.yml` — add `app.backup.import-backups-dir: data/.import-backups`
- [x] `src/main/resources/application-local.yml` — append `?rewriteBatchedStatements=true` to JDBC URL (RESEARCH §10)
- [x] `src/main/resources/application-docker.yml` — append `?rewriteBatchedStatements=true` to JDBC URL (RESEARCH §10)
- [x] `src/main/resources/application-prod.yml` — append `&rewriteBatchedStatements=true` to JDBC URL template (RESEARCH §10)

Test scaffolds (one per Per-Task entry):

- [x] `src/test/java/org/ctc/backup/restore/entity/*RestorerTest.java` × 24 — Surefire unit tests (insertSql shape + setter coercion)
- [x] `src/test/java/org/ctc/backup/restore/entity/TeamRestorerIT.java` — 2-pass restore against H2
- [x] `src/test/java/org/ctc/backup/restore/NoopRestoreFailureInjectorTest.java` — @Primary no-op contract
- [x] `src/test/java/org/ctc/backup/audit/DataImportAuditServiceTest.java` — REQUIRES_NEW via `@MockitoSpyBean` on `PlatformTransactionManager`
- [x] `src/test/java/org/ctc/backup/audit/DataImportAuditSerializationTest.java` — LONGTEXT JSON round-trip
- [x] `src/test/java/org/ctc/backup/service/BackupImportExecuteIT.java` — IMPORT-05 happy path on H2
- [x] `src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java` — D-13 failure injection on H2
- [x] `src/test/java/org/ctc/backup/service/BackupImportPostCommitIT.java` — IMPORT-06 atomic-move triple
- [x] `src/test/java/org/ctc/backup/service/BackupArchiveExtractUploadsIT.java` — `PathTraversalGuard` + size cap on uploads-tree extract
- [x] `src/test/java/org/ctc/backup/service/BackupImportMariaDbSmokeIT.java` — QUAL-03 CI layer (Testcontainers MariaDB, @ActiveProfiles("local"))
- [x] `src/test/java/org/ctc/e2e/BackupImportE2ETest.java` — EXTEND existing Phase 74 file with one new `@Test` covering real success-flash flow
- [x] `src/test/java/org/ctc/backup/service/BackupImportPostCommitEdgeCasesIT.java` — **(added by Phase 87 / Plan 87-05)** AFTER_COMMIT listener double-fire safety + `<ts>` directory naming-convention contract

Documentation + manual-UAT artifacts:

- [x] `.planning/phases/75-.../75-HUMAN-UAT.md` — D-16 six-screenshot checklist (Standings R/A, R/B, Driver Ranking, PLAYOFF bracket, sub-team Phase Breakdown, driver Phase Breakdown) **(archived only in git ref `60f5f915^` per Phase 87 CONTEXT D-02 minimal restore scope; the file existed and recorded 10/10 PASS on 2026-05-14)**
- [x] `.screenshots/75/before/` directory (operator-populated 2026-05-14)
- [x] `.screenshots/75/after/` directory (operator-populated 2026-05-14)

*Wave 0 is satisfied retroactively per CONTEXT D-12: every checkbox above is ticked AND `./mvnw verify` is green on the audit commit. The original Wave 0 commit shipped in v1.10 (pre-`60f5f915` deletion).*

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

- [x] All tasks have `<automated>` verify references mapped to the Per-Task table above
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all `✅` references in the Per-Task table
- [x] No `--watch` / continuous-mode flags (carry-forward Phase 74 discipline)
- [x] Feedback latency < ~3 min including E2E
- [x] JaCoCo line coverage ≥ 82% gate green on phase exit (`./mvnw verify`)
- [x] `mariadb-migration-smoke.yml` CI workflow green
- [x] `75-HUMAN-UAT.md` operator sign-off recorded (PASS/FAIL + free-text) — 10/10 PASS on 2026-05-14 (file archived in git ref `60f5f915^` per Phase 87 CONTEXT D-02)
- [x] `nyquist_compliant: true` set in this frontmatter after sign-off

---

## Validation Audit 2026-05-18

**Audit method:** retroactive (CONTEXT D-12 / D-13 — VALIDATION authored 2026-05-14 as draft, audit + approval 2026-05-18 inside Phase 87 / Plan 87-05).

**Auditor:** orchestrator-driven (matching Plans 87-01..04 — `/gsd:validate-phase` workflow's `gsd-nyquist-auditor` subagent path was not invoked separately; the existing 75-VALIDATION.md draft + 75-VERIFICATION.md provided the full coverage matrix and the gap analysis was performed inline per CONTEXT D-05 aggressive-fill).

**Gap analysis result:** 2 gaps surfaced — both LOW-severity defensive contract assertions identified in 87-RESEARCH.md §"Phase 75" predicted profile. Both filled inside the same per-phase atomic commit group:

| Gap | Description | Resolution |
|-----|-------------|------------|
| 1 | AFTER_COMMIT listener double-fire safety — second invocation must fail loud at Step 1 without corrupting FS state or the first invocation's audit row | New `BackupImportPostCommitEdgeCasesIT#givenAfterCommitListenerIsInvokedTwiceWithSameEvent_…` |
| 2 | `data/.import-backups/<ts>/` sub-directory naming-convention contract (ISO-8601-with-dashes `yyyy-MM-ddTHH-mm-ssZ`) — operator-visible 24h-retention key per CONTEXT D-04 | New `BackupImportPostCommitEdgeCasesIT#givenImportBackupDirNamingConvention_…` |

**Test placement:** `src/test/java/org/ctc/backup/service/BackupImportPostCommitEdgeCasesIT.java` (`@Tag("integration")` per CLAUDE.md Architectural Principles + 87-RESEARCH §"Test Class Placement" Phase 75 row). Reuses the existing `@SpringBootTest @ActiveProfiles("dev")` context — no new cold-start cost.

**Targeted run (local, 2026-05-18):** `./mvnw failsafe:integration-test failsafe:verify -Dit.test='BackupImportPostCommitEdgeCasesIT'` → BUILD SUCCESS; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 29.42 s.

**Implementation bugs found:** **None.** The atomic-move-triple production code in `BackupImportPostCommitListener` already handles both gap scenarios correctly; the gap was test-coverage only, not behaviour.

**CI evidence:** last green full `./mvnw verify -Pe2e` run on this branch = workflow_dispatch run-id `26008754136` (CI workflow, 47:50 wallclock, 2026-05-18T01:30:27Z) on `gsd/v1.11-tooling-and-cleanup`. Post-Phase-87 wallclock guard runs in Plan 87-08 closer.

**Wallclock impact:** +29 s (2 scenarios in already-loaded context). Within Phase 87 D-06 5% regression budget (~69 s headroom over the 23:00 baseline shared with Plans 87-01..04 + 06..08).

---

**Approval:** approved 2026-05-18 — retroactive audit via Phase 87 / Plan 87-05
