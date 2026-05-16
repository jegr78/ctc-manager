---
phase: 76
slug: operational-hardening-import-lock-read-only-banner-auto-back
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-14
---

# Phase 76 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution. Derived from `76-RESEARCH.md` §Validation Architecture.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Spring Boot Test 4.0.6 (Surefire for unit, Failsafe for IT) |
| **Config file** | `pom.xml` (Surefire/Failsafe plugin sections); `application-dev.yml` for IT profile |
| **Quick run command** | `./mvnw test -Dtest=ImportLockServiceTest` (unit, ~3 s) |
| **Full suite command** | `./mvnw verify` (Surefire + Failsafe, ~3 min); `./mvnw verify -Pe2e` (with Playwright, ~6 min) |
| **Estimated runtime** | Unit: ~3 s · Failsafe ITs (Phase 76): ~2 min · Full verify: ~3 min · `-Pe2e` gate: ~6 min |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -Dtest=ImportLockServiceTest` for any Ring-1 change; targeted `-Dit.test=<ITName>` for Ring-2/3 changes (≤ 60 s feedback).
- **After every plan wave:** Run `./mvnw verify -Dit.test=ImportConcurrentLockIT,ImportLockedPostRejectorIT,ImportLockBannerAdviceIT,AutoBackupBeforeImportPathIT,AutoBackupBeforeImportFailureIT` (~2 min).
- **Before `/gsd-verify-work`:** Full `./mvnw verify -Pe2e` must be green (CLAUDE.md `feedback_e2e_verification`).
- **Max feedback latency:** 60 s for task-commit sampling; 2 min for wave sampling; 6 min for phase gate.

---

## Per-Task Verification Map

> Plan IDs are tentative; planner finalizes them in PLAN.md files. Test classes are the binding contract per Wave 0.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 76-01-01 | 01 | 1 | SECU-05 | T-76-01 (Tampering: concurrent destructive op race) | Second concurrent `tryLock()` returns false; first still holds | unit | `./mvnw test -Dtest=ImportLockServiceTest` | ❌ Wave 0 | ⬜ pending |
| 76-01-02 | 01 | 1 | SECU-05 | T-76-01 | `unlock()` from non-holding thread is silent no-op (idempotent) | unit | `./mvnw test -Dtest=ImportLockServiceTest` | ❌ Wave 0 | ⬜ pending |
| 76-01-03 | 01 | 1 | SECU-05 | T-76-01 | 2-thread race: thread-B sees HTTP 409 + Flash; thread-A completes 1 audit row | IT | `./mvnw verify -Dit.test=ImportConcurrentLockIT` | ❌ Wave 0 | ⬜ pending |
| 76-01-04 | 01 | 1 | SECU-05 | T-76-04 (Info Disclosure: 409 status leak on redirect) | View-mode redirect surfaces HTTP 409 (not 302) | IT (within ImportConcurrentLockIT) | `./mvnw verify -Dit.test=ImportConcurrentLockIT` | ❌ Wave 0 | ⬜ pending |
| 76-02-01 | 02 | 2 | SECU-06 | T-76-05 (Elevation: whitelist bypass) | Non-whitelisted POST during lock → HTTP 503; `equals` match prevents `/admin/backup/import-execute-foo` slip (D-10) | IT | `./mvnw verify -Dit.test=ImportLockedPostRejectorIT` | ❌ Wave 0 | ⬜ pending |
| 76-02-02 | 02 | 2 | SECU-06 | T-76-02 (Tampering: missing banner) | Banner text "Backup import in progress — write access is temporarily locked." visible on `GET /admin/seasons` during held lock | IT | `./mvnw verify -Dit.test=ImportLockBannerAdviceIT` | ❌ Wave 0 | ⬜ pending |
| 76-02-03 | 02 | 2 | SECU-06 | T-76-02 | Banner absent on site templates (D-13) | IT (within ImportLockBannerAdviceIT) | `./mvnw verify -Dit.test=ImportLockBannerAdviceIT` | ❌ Wave 0 | ⬜ pending |
| 76-03-01 | 03 | 3 | SECU-07 | T-76-08 (Data Protection: pre-wipe snapshot missing) | After successful import, `data/.import-backups/<ts>/auto-backup-before-import.zip` exists with same `<ts>` as uploads-old sibling | IT | `./mvnw verify -Dit.test=AutoBackupBeforeImportPathIT` | ❌ Wave 0 | ⬜ pending |
| 76-03-02 | 03 | 3 | SECU-07 | T-76-03 (Repudiation: audit row loss across rollback) | Auto-export IOException → no wipe (row counts unchanged), audit row exists `success=false` + empty `table_counts_wiped`/`table_counts_restored` JSON, Flash matches D-17, partial ZIP cleaned | IT | `./mvnw verify -Dit.test=AutoBackupBeforeImportFailureIT` | ❌ Wave 0 | ⬜ pending |
| 76-03-03 | 03 | 3 | SECU-07 | T-76-06 (DoS: lock leak on AutoBackup failure) | After AutoBackupBeforeImportException, second import attempt succeeds (lock released by finally) | IT (within ImportConcurrentLockIT) | `./mvnw verify -Dit.test=ImportConcurrentLockIT` | ❌ Wave 0 | ⬜ pending |
| 76-04-01 | 04 | 4 | SECU-07 (doc) | — | `docs/operations/import-runbook.md` exists with 5 locked sections (D-22) | source assertion | `test -f docs/operations/import-runbook.md && grep -q "Recovery from auto-backup" docs/operations/import-runbook.md` | ❌ Wave 0 | ⬜ pending |
| 76-04-02 | 04 | 4 | All | — | Coverage ≥ 82% (CLAUDE.md constraint); full E2E suite BUILD SUCCESS | gate | `./mvnw verify -Pe2e` then `cat target/site/jacoco/jacoco.csv` | ✓ existing | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

New test files Phase 76 must create before the first task can sample:

- [ ] `src/test/java/org/ctc/backup/lock/ImportLockServiceTest.java` — unit covers `tryLock()` non-blocking, `unlock()` idempotency via `isHeldByCurrentThread()`, `isLocked()` read (SECU-05 unit-level).
- [ ] `src/test/java/org/ctc/backup/it/support/BlockingRestoreFailureInjector.java` — test-only `@Primary` injector reusing the Phase 75 `FailAtTableInjector.Config` bean-override discipline (`@Bean(name = "noopRestoreFailureInjector")` + `spring.main.allow-bean-definition-overriding=true`). Shared by IT-1 / IT-2 / IT-3.
- [ ] `src/test/java/org/ctc/backup/it/ImportConcurrentLockIT.java` — 2-thread Failsafe IT (D-20). Drives slow import via `BlockingRestoreFailureInjector`; asserts thread-B HTTP 409 + Flash + thread-A completes + exactly 1 audit row.
- [ ] `src/test/java/org/ctc/backup/it/ImportLockedPostRejectorIT.java` — drives slow import; fires whitelisted POST (`/admin/backup/import-execute` — still rejected by lock service with 409, NOT by interceptor with 503) and non-whitelisted POST (e.g. `/admin/teams`); asserts 503 (D-21).
- [ ] `src/test/java/org/ctc/backup/it/ImportLockBannerAdviceIT.java` — drives slow import; fires `GET /admin/seasons`; asserts banner text in response body; fires `GET /` (site index) — asserts banner absent (D-13).
- [ ] `src/test/java/org/ctc/backup/it/AutoBackupBeforeImportPathIT.java` — happy path: import succeeds → `auto-backup-before-import.zip` exists at expected path with same `<ts>` as `uploads-old/` sibling.
- [ ] `src/test/java/org/ctc/backup/it/AutoBackupBeforeImportFailureIT.java` — IOException injection on auto-export write (e.g., pre-create directory with conflicting file to trigger `CREATE_NEW` failure); asserts: no wipe (row counts unchanged), audit row `success=false` with empty count maps, Flash matches D-17 wording, partial ZIP cleaned via `tryDeletePartialAutoBackup`.
- Framework install: none — JUnit 5 + Spring Boot Test 4.0.6 already configured.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Operator UX for HTTP 409 redirect (banner on `/admin/backup`) | SECU-05 | Confirms the redirect+409 actually renders the locked-state Flash to a real browser | 1. Start dev profile + demo data. 2. Open `/admin/backup` in tab A, upload+confirm import (use `BlockingRestoreFailureInjector` or a real large fixture). 3. Open `/admin/backup` in tab B, attempt same import. 4. Tab B should show the error flash; DevTools Network shows HTTP 409. |
| Banner visibility while lock held across full admin nav | SECU-06 | Cross-page navigation verification a single IT can't span | 1. Trigger slow import. 2. Navigate to `/admin/seasons`, `/admin/teams`, `/admin/drivers`, `/admin/matchdays`, `/admin/races` — each must show the yellow banner. 3. Wait for completion — banner disappears. |
| Runbook clarity for off-hour ops | SECU-07 (doc, D-22) | Human readability check on operational recovery flow | Walk a peer through `docs/operations/import-runbook.md` cold; they must complete each of the 5 recovery scenarios without further questions. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify command or Wave 0 dependency
- [ ] Sampling continuity: every 3 consecutive tasks include at least one automated verify
- [ ] Wave 0 covers all NEW test files (8 listed above)
- [ ] No watch-mode flags (one-shot test runs only — CLAUDE.md `feedback_test_call_optimization`)
- [ ] Feedback latency < 60 s per task / < 2 min per wave / < 6 min phase gate
- [ ] `nyquist_compliant: true` set in frontmatter after plan-checker pass

**Approval:** pending
