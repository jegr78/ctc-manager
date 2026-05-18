---
phase: 76
slug: operational-hardening-import-lock-read-only-banner-auto-back
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-14
approved_on: 2026-05-18
audit_method: retroactive
---

# Phase 76 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution. Derived from `76-RESEARCH.md` §Validation Architecture. Approved retroactively via Phase 87 / Plan 87-06 (audit_method: retroactive).

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
- **After every plan wave:** Run `./mvnw verify -Dit.test=ImportConcurrentLockIT,ImportLockedPostRejectorIT,ImportLockBannerAdviceIT,AutoBackupBeforeImportPathIT,AutoBackupBeforeImportFailureIT,AutoBackupCatchOrderIT` (~2 min).
- **Before `/gsd-verify-work`:** Full `./mvnw verify -Pe2e` must be green (CLAUDE.md `feedback_e2e_verification`).
- **Max feedback latency:** 60 s for task-commit sampling; 2 min for wave sampling; 6 min for phase gate.

---

## Per-Task Verification Map

> Plan IDs are tentative; planner finalizes them in PLAN.md files. Test classes are the binding contract per Wave 0.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 76-01-01 | 01 | 1 | SECU-05 | T-76-01 (Tampering: concurrent destructive op race) | Second concurrent `tryLock()` returns false; first still holds | unit | `./mvnw test -Dtest=ImportLockServiceTest` | ✅ `src/test/java/org/ctc/backup/lock/ImportLockServiceTest.java` | ✅ green |
| 76-01-02 | 01 | 1 | SECU-05 | T-76-01 | `unlock()` from non-holding thread is silent no-op (idempotent) | unit | `./mvnw test -Dtest=ImportLockServiceTest` | ✅ `src/test/java/org/ctc/backup/lock/ImportLockServiceTest.java` | ✅ green |
| 76-01-03 | 01 | 1 | SECU-05 | T-76-01 | 2-thread race: thread-B sees HTTP 409 + Flash; thread-A completes 1 audit row | IT | `./mvnw verify -Dit.test=ImportConcurrentLockIT` | ✅ `src/test/java/org/ctc/backup/it/ImportConcurrentLockIT.java` | ✅ green |
| 76-01-04 | 01 | 1 | SECU-05 | T-76-04 (Info Disclosure: 409 status leak on redirect) | View-mode redirect surfaces HTTP 409 (not 302) | IT (within ImportConcurrentLockIT) | `./mvnw verify -Dit.test=ImportConcurrentLockIT` | ✅ `src/test/java/org/ctc/backup/it/ImportConcurrentLockIT.java` | ✅ green |
| 76-02-01 | 02 | 2 | SECU-06 | T-76-05 (Elevation: whitelist bypass) | Non-whitelisted POST during lock → HTTP 503; `equals` match prevents `/admin/backup/import-execute-foo` slip (D-10) | IT + unit | `./mvnw verify -Dit.test=ImportLockedPostRejectorIT` + `./mvnw test -Dtest=ImportLockedWriteRejectorTest` | ✅ `src/test/java/org/ctc/backup/it/ImportLockedPostRejectorIT.java` + `src/test/java/org/ctc/backup/lock/ImportLockedWriteRejectorTest.java` (87-06 gap-fill) | ✅ green |
| 76-02-02 | 02 | 2 | SECU-06 | T-76-02 (Tampering: missing banner) | Banner text "Backup import in progress — write access is temporarily locked." visible on `GET /admin/seasons` during held lock | IT | `./mvnw verify -Dit.test=ImportLockBannerAdviceIT` | ✅ `src/test/java/org/ctc/backup/it/ImportLockBannerAdviceIT.java` | ✅ green |
| 76-02-03 | 02 | 2 | SECU-06 | T-76-02 | Banner absent on site templates (D-13) | IT (within ImportLockBannerAdviceIT) | `./mvnw verify -Dit.test=ImportLockBannerAdviceIT` | ✅ `src/test/java/org/ctc/backup/it/ImportLockBannerAdviceIT.java` | ✅ green |
| 76-03-01 | 03 | 3 | SECU-07 | T-76-08 (Data Protection: pre-wipe snapshot missing) | After successful import, `data/.import-backups/<ts>/auto-backup-before-import.zip` exists with same `<ts>` as uploads-old sibling | IT | `./mvnw verify -Dit.test=AutoBackupBeforeImportPathIT` | ✅ `src/test/java/org/ctc/backup/it/AutoBackupBeforeImportPathIT.java` | ✅ green |
| 76-03-02 | 03 | 3 | SECU-07 | T-76-03 (Repudiation: audit row loss across rollback) | Auto-export IOException → no wipe (row counts unchanged), audit row exists `success=false` + empty `table_counts_wiped`/`table_counts_restored` JSON, Flash matches D-17, partial ZIP cleaned | IT | `./mvnw verify -Dit.test=AutoBackupBeforeImportFailureIT` | ✅ `src/test/java/org/ctc/backup/it/AutoBackupBeforeImportFailureIT.java` | ✅ green |
| 76-03-03 | 03 | 3 | SECU-07 | T-76-06 (DoS: lock leak on AutoBackup failure) | After AutoBackupBeforeImportException, second import attempt succeeds (lock released by finally) | IT (within AutoBackupBeforeImportFailureIT) | `./mvnw verify -Dit.test=AutoBackupBeforeImportFailureIT` | ✅ `src/test/java/org/ctc/backup/it/AutoBackupBeforeImportFailureIT.java` (Test 3: `givenAutoExportFails_whenControllerExecutes_thenLockIsReleased`) | ✅ green |
| 76-03-04 | 03 | 3 | SECU-07 | T-76-09 (Logic: catch-chain reordering) | `AutoBackupBeforeImportException` (subclass) caught BEFORE `BackupImportException` (parent) — Flash matches D-17 subclass-branch wording | IT (87-06 gap-fill) | `./mvnw verify -Dit.test=AutoBackupCatchOrderIT` | ✅ `src/test/java/org/ctc/backup/it/AutoBackupCatchOrderIT.java` (87-06 gap-fill) | ✅ green |
| 76-04-01 | 04 | 4 | SECU-07 (doc) | — | `docs/operations/import-runbook.md` exists with 5 locked sections (D-22) | source assertion | `test -f docs/operations/import-runbook.md && grep -q "Recovery from auto-backup" docs/operations/import-runbook.md` | ✅ `docs/operations/import-runbook.md` | ✅ green |
| 76-04-02 | 04 | 4 | All | — | Coverage ≥ 82% (CLAUDE.md constraint); full E2E suite BUILD SUCCESS | gate | `./mvnw verify -Pe2e` then `cat target/site/jacoco/jacoco.csv` | ✅ existing | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Satisfied retroactively — all referenced test files exist on disk as of Phase 87 / Plan 87-06 (audit_method: retroactive):

- [x] `src/test/java/org/ctc/backup/lock/ImportLockServiceTest.java` — unit covers `tryLock()` non-blocking, `unlock()` idempotency via `isHeldByCurrentThread()`, `isLocked()` read (SECU-05 unit-level). 6 tests.
- [x] `src/test/java/org/ctc/backup/lock/ImportLockedWriteRejectorTest.java` — **NEW (87-06 gap-fill)** — pins SECU-06 whitelist-on-equals contract (D-10) + verb / no-lock short-circuit branches. 6 unit tests.
- [x] `src/test/java/org/ctc/backup/it/support/BlockingRestoreFailureInjector.java` — test-only `@Primary` injector reusing the Phase 75 `FailAtTableInjector.Config` bean-override discipline. Shared by IT-1 / IT-2 / IT-3.
- [x] `src/test/java/org/ctc/backup/it/ImportLockServiceResetHelper.java` — `@TestComponent` reset bean (Phase 86 D-03) replacing per-method `@DirtiesContext`.
- [x] `src/test/java/org/ctc/backup/it/ImportConcurrentLockIT.java` — 2-thread Failsafe IT (D-20). Drives slow import via `BlockingRestoreFailureInjector`; asserts thread-B HTTP 409 + Flash + thread-A completes + exactly 1 audit row.
- [x] `src/test/java/org/ctc/backup/it/ImportLockedPostRejectorIT.java` — drives slow import; fires whitelisted POST (`/admin/backup/import-execute` — still rejected by lock service with 409, NOT by interceptor with 503) and non-whitelisted POST (`/admin/teams/save`); asserts 503 (D-21). 4 BDD scenarios.
- [x] `src/test/java/org/ctc/backup/it/ImportLockBannerAdviceIT.java` — drives slow import; fires `GET /admin/seasons`; asserts banner text + CSS class + ARIA role in response body; fires `GET /` (site index) — asserts banner absent (D-13). 3 BDD scenarios.
- [x] `src/test/java/org/ctc/backup/it/AutoBackupBeforeImportPathIT.java` — happy path: import succeeds → `auto-backup-before-import.zip` exists at expected path with same `<ts>` as `uploads-old/` sibling; manifest schemaVersion matches.
- [x] `src/test/java/org/ctc/backup/it/AutoBackupBeforeImportFailureIT.java` — IOException injection via `@MockitoSpyBean` on `BackupArchiveService.writeZip`; asserts: no wipe (row counts unchanged), audit row `success=false` with empty count maps (D-18), partial ZIP cleaned via `tryDeletePartialAutoBackup` (D-19), lock released after AutoBackup failure (T-76-06 mode E).
- [x] `src/test/java/org/ctc/backup/it/AutoBackupCatchOrderIT.java` — **NEW (87-06 gap-fill)** — pins SECU-07 catch-chain ordering: `AutoBackupBeforeImportException` (subclass) caught BEFORE `BackupImportException` (parent) so the operator-visible Flash matches D-17 subclass-branch wording, never the parent-branch wording.
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

- [x] All tasks have `<automated>` verify command or post-hoc evidence
- [x] Sampling continuity: every 3 consecutive tasks include at least one automated verify
- [x] Wave 0 covers all NEW test files (10 listed above; 2 added retroactively in Plan 87-06)
- [x] No watch-mode flags (one-shot test runs only — CLAUDE.md `feedback_test_call_optimization`)
- [x] Feedback latency < 60 s per task / < 2 min per wave / < 6 min phase gate
- [x] `nyquist_compliant: true` set in frontmatter after plan-checker pass

**Approval:** approved 2026-05-18 — retroactive audit via Phase 87 / Plan 87-06

---

## Validation Audit 2026-05-18

**Audit method:** retroactive (Phase 87 / Plan 87-06 — `audit_method: retroactive`)
**Auditor:** orchestrator-driven nyquist audit; gap-coverage decision rule from Phase 87 CONTEXT D-05 (aggressive gap-filling)
**Sampling:** Per-task verification map cross-checked against test files on disk; `ImportLockedWriteRejector.java` (production) + `BackupController.java` line 213 (catch-order) inspected for D-10 / D-17 conformance.

### Gap counts

| Requirement | Pre-audit Status | Gap Tests Added | Post-audit Status |
|-------------|-------------------|-----------------|--------------------|
| SECU-05 | COVERED (`ImportLockServiceTest` + `ImportConcurrentLockIT`) | 0 | ✅ COVERED |
| SECU-06 | PARTIAL — D-10 whitelist-on-equals not pinned by a regression test (existing IT covers `/admin/teams/save` only) | 1 — `ImportLockedWriteRejectorTest` (6 unit cases incl. `/admin/backup/import-execute-anything`, `/admin/backups-fake`) | ✅ COVERED |
| SECU-07 | PARTIAL — catch-chain order (subclass before parent, D-17 line 213) not pinned by a Flash-text regression test | 1 — `AutoBackupCatchOrderIT` (1 IT case asserting D-17 subclass-branch wording) | ✅ COVERED |

**Total gap tests added:** 2 (6 unit + 1 IT cases). Matches predicted gap profile from 87-RESEARCH.md (HIGH likelihood — predicted 2-4, delivered 2).

### Implementation bugs found by auditor

**None.** Both predicted-gap surfaces were correctly implemented in the production code; the gap was purely on the test-side (no regression guard pinning the contract). Specifically:

- `ImportLockedWriteRejector.java` line 68 uses `String.equals(requestURI)` (D-10 conformant — no startsWith smuggling).
- `BackupController.java` line 213 catches `AutoBackupBeforeImportException` BEFORE line 225 `BackupImportException` (D-17 conformant — Java first-match-wins resolves to subclass branch).

No `fix(76):` commits required. No CONTEXT D-08 trivial-fix invocation, no non-trivial impl-bug escalation per CONTEXT D-08 Deferred Idea #1.

### CI evidence

- **Local Surefire run (gap-fill unit):** `./mvnw test -Dtest='ImportLockedWriteRejectorTest'` — Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.651 s — BUILD SUCCESS (2026-05-18 09:57:06)
- **Local Failsafe run (gap-fill IT):** `./mvnw failsafe:integration-test failsafe:verify -Dit.test='AutoBackupCatchOrderIT'` — Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 29.62 s — BUILD SUCCESS (2026-05-18 09:57:42)
- **Pre-restore CI baseline:** `gh run 26015174092` (head SHA 63458a74) — success on `gsd/v1.11-tooling-and-cleanup` HEAD prior to gap-fill push; final post-restore CI assertion runs at v1.11 milestone-PR close per CONTEXT D-14.

### TDD gate compliance

Not applicable — retroactive audit. Wave 0 contract (test classes named before implementation) was honored during original Phase 76 execution per existing 76-01..76-04 SUMMARYs; the two 87-06 gap-fill tests are regression guards pinning already-shipped production behavior.

### No-@DirtiesContext compliance

Both new test classes verified clean of `@DirtiesContext` annotations (`grep -c @DirtiesContext` returns 0 on both files). Per Phase 86 D-03, the existing IT lock-reset pattern uses `ImportLockServiceResetHelper`; the new IT uses `@MockitoSpyBean` with `Mockito.reset()` in `@BeforeEach`, no `ImportLockService` state interaction.
