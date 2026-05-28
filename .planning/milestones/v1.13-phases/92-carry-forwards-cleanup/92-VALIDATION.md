---
phase: 92
slug: carry-forwards-cleanup
status: shipped
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-21
---

# Phase 92 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from `92-RESEARCH.md` § Validation Architecture.
> Per CONTEXT D-08, each of the 4 plans also ships its own per-plan VALIDATION.md.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter 5 + Mockito (Spring Boot 4.x managed) + AssertJ |
| **Config file** | `pom.xml` (Surefire + Failsafe + `-Pe2e` profile) |
| **Quick run command** | `./mvnw test -Dtest='CsvImportControllerExceptionTest,RaceControllerCalendarTest,AssumptionsFencePredicateTest'` |
| **Targeted IT run** | `./mvnw verify -Dit.test='GoogleSheetsServiceIT,GoogleCalendarServiceIT'` (only if Open Q1 → new IT siblings) |
| **Full suite command** | `./mvnw verify` |
| **Phase-gate command** | `./mvnw verify -Pe2e` (CLAUDE.md § Commands + Phase 91 D-12 precedent) |
| **Estimated runtime** | quick ~5s, verify ~3min, verify -Pe2e ~17min (v1.12 CI median 17:39) |

---

## Sampling Rate

- **After every task commit:** `./mvnw test -Dtest='<scoped test class>'` (~5s)
- **After every plan ship:** `./mvnw verify` (JaCoCo CSV regenerates; SpotBugs gate; CodeQL on PR push)
- **Before `/gsd-validate-phase 92`:** `./mvnw verify -Pe2e` (full E2E budget)
- **Max feedback latency:** ~5s for unit subset; ~3min for full Surefire+Failsafe

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 92-01-01 | 01 | 1 | UX-01 | T-91-02-IL | CsvImportController.previewSheet AUTH path → flash `AUTH_MESSAGE` + `errorCategory=AUTH`, no `e.getMessage()` echo | unit (MockMvc) | `./mvnw test -Dtest='CsvImportControllerExceptionTest#givenAuthFailure_whenPreviewSheet_thenRendersAuthBadge'` | ✅ | ✅ green |
| 92-01-02 | 01 | 1 | UX-01 | T-91-02-IL | previewSheet NOT_FOUND path → `NOT_FOUND_MESSAGE` + `errorCategory=NOT_FOUND` | unit (MockMvc) | `./mvnw test -Dtest='CsvImportControllerExceptionTest#givenNotFound_whenPreviewSheet_thenRendersNotFoundBadge'` | ✅ | ✅ green |
| 92-01-03 | 01 | 1 | UX-01 | T-91-02-IL | previewSheet PERMISSION path → `PERMISSION_MESSAGE` + `errorCategory=PERMISSION` | unit (MockMvc) | `./mvnw test -Dtest='CsvImportControllerExceptionTest#givenPermissionDenied_whenPreviewSheet_thenRendersPermissionBadge'` | ✅ | ✅ green |
| 92-01-04 | 01 | 1 | UX-01 | T-91-02-IL | previewSheet TRANSIENT path (new dedicated `givenTransientFailure_whenPreviewSheet_thenRendersTransientBadge`; legacy `givenIoException_whenPreviewSheet` rewritten) | unit (MockMvc) | `./mvnw test -Dtest='CsvImportControllerExceptionTest#givenTransientFailure_whenPreviewSheet_thenRendersTransientBadge'` | ✅ | ✅ green |
| 92-01-05 | 01 | 1 | UX-01 | T-91-02-IL | execute() — 4 typed-catch arms via redirect+flash | unit (MockMvc) | 4 new `@Test` methods on `CsvImportControllerExceptionTest` | ✅ | ✅ green |
| 92-01-06 | 01 | 1 | UX-01 | T-91-02-IL | Templates `admin/import.html` + `admin/import-preview.html` render badge block (verbatim copy from `driver-import.html`); auto-escaped via `th:text` | Thymeleaf parse + existing E2E | `./mvnw verify` boots app + renders templates; visual UAT outstanding (post-deploy) | ✅ | ✅ green |
| 92-02-01 | 02 | 1 | COV-01 | — | RaceController GET `/admin/races/{id}` — `calendarAvailable` model attribute set | unit (MockMvc standalone) | `./mvnw test -Dtest='RaceControllerCalendarTest#givenCalendarAvailable_whenGetRaceDetail_thenModelHasCalendarAvailableTrue'` | ✅ | ✅ green |
| 92-02-02 | 02 | 1 | COV-01 | — | RaceController GET — `hasCalendarEvent` + `canCreateCalendarEvent` branches | unit (MockMvc standalone) | 2 `@Test` methods on `RaceControllerCalendarTest` | ✅ | ✅ green |
| 92-02-03 | 02 | 1 | COV-01 | — | RaceController POST `/create-calendar-event` — 4 typed-catch arms (AUTH/NOT_FOUND/PERMISSION/TRANSIENT) | unit (MockMvc) | 4 `@Test` methods on `RaceControllerCalendarTest` | ✅ | ✅ green |
| 92-02-04 | 02 | 1 | COV-01 | — | RaceController POST — `IllegalStateException` catch path | unit (MockMvc) | `givenCalendarIllegalState_whenPostCreateCalendarEvent_thenRedirectsWithPlainMessage` | ✅ | ✅ green |
| 92-02-05 | 02 | 1 | COV-01 | — | `GoogleApiExceptionMapper` IOException → `TransientGoogleApiException` default mapping (real Google client) | IT (`@SpringBootTest`+`@Tag("integration")`) | `./mvnw verify -Dit.test='GoogleSheetsServiceIT'` | ✅ | ✅ green |
| 92-02-06 | 02 | 1 | COV-01 | — | `GoogleApiExceptionMapper` 403 reason `authError` → `AuthGoogleApiException` (not Permission); GeneralSecurityException → Auth | IT | `./mvnw verify -Dit.test='GoogleCalendarServiceIT'` | ✅ | ✅ green |
| 92-02-07 | 02 | 1 | COV-01 | — | JaCoCo line coverage ≥ 88.88 % | gate | `./mvnw verify` + header-validated awk on `target/site/jacoco/jacoco.csv` | ✅ | ✅ green (88.8838 %) |
| 92-03-01 | 03 | 1 | CLEAN-01 | — | `assumptions-fence` triggers on `import static org.junit.jupiter.api.Assumptions.assumeFalse` (synthetic positive in `@TempDir`) | unit | `./mvnw test -Dtest='AssumptionsFencePredicateTest#givenJunitAssumptionsImport_whenPredicateRuns_thenViolationDetected'` | ✅ | ✅ green |
| 92-03-02 | 03 | 1 | CLEAN-01 | — | `assumptions-fence` does NOT trigger on `import static org.assertj.core.api.Assumptions.assumeThat` (synthetic negative in `@TempDir`) | unit | `./mvnw test -Dtest='AssumptionsFencePredicateTest#givenAssertjAssumptionsImport_whenPredicateRuns_thenNoViolation'` | ✅ | ✅ green |
| 92-03-03 | 03 | 1 | CLEAN-01 | — | `./mvnw validate` exit 0 on current codebase (BackupStagingDirPerForkIT.java:12 AssertJ import does NOT trigger fence) | gate | `./mvnw validate` → fence OK echo | ✅ | ✅ green |
| 92-04-01 | 04 | 1 | DOCS-01 | — | 3 retroactive `89-VERIFICATION.md`, `90-VERIFICATION.md`, `91-VERIFICATION.md` exist under `.planning/milestones/v1.12-phases/{89,90,91}-*/` | file existence | `test -f "$(ls -d .planning/milestones/v1.12-phases/89-*)/89-VERIFICATION.md"` + same for 90/91 | ✅ | ✅ green |
| 92-04-02 | 04 | 1 | DOCS-01 | — | Each VERIFICATION.md contains required section headers (Goal Achievement — Success Criteria + Per-Dimension Verdict Table) + `audit_method: retroactive` front-matter | grep | per-file `grep -q "Goal Achievement — Success Criteria"` + `grep -q "Per-Dimension Verdict Table"` + `grep -q "audit_method: retroactive"` | ✅ | ✅ green |
| 92-04-03 | 04 | 1 | BOOK-01 | — | 7 stale `[ ]` checkboxes flipped to `[x]` | grep count | `grep -c '^- \[ \]' .planning/milestones/v1.12-REQUIREMENTS.md` returns 0 | ✅ | ✅ green |
| 92-04-04 | 04 | 1 | BOOK-01 | — | 4 stale `Pending` rows flipped to `Resolved` | grep count | `grep -c 'Pending' .planning/milestones/v1.12-REQUIREMENTS.md` returns 0 | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `src/test/java/org/ctc/dataimport/CsvImportControllerExceptionTest.java` — extended in place (Plan 92-01 + Plan 92-02 happy-path additions): 16 `@Test` methods total covering 8 typed-permit paths + 4 happy/empty branches + 4 legacy method paths; all use Hamcrest `equalTo` on typed Google catches to encode the T-91-02-IL invariant
- [x] `src/test/java/org/ctc/admin/controller/RaceControllerCalendarTest.java` — NEW (Plan 92-02): 9 `@Test` methods; GETs use `MockMvcBuilders.standaloneSetup` with a noop view-resolver to skip Thymeleaf rendering on the heavy `race-detail` template
- [x] `src/test/java/org/ctc/build/AssumptionsFencePredicateTest.java` — NEW greenfield package `org.ctc.build` (Plan 92-03): 2 `@Test` methods × `@TempDir` + `ProcessBuilder`; `FENCE_REGEX` constant locks the test predicate visually against the pom.xml predicate
- [x] `src/test/java/org/ctc/dataimport/GoogleSheetsServiceIT.java` — NEW IT sibling (Plan 92-02): 3 `@Test` methods, `@Tag("integration")` routes via Failsafe
- [x] `src/test/java/org/ctc/dataimport/GoogleCalendarServiceIT.java` — NEW IT sibling (Plan 92-02): 3 `@Test` methods covering 403-discriminator + GeneralSecurityException paths
- [x] No new framework — JUnit 5, Mockito, AssertJ, Spring Boot Test, MockMvc all already on classpath

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Plan 92-01 visual rendering of `.error-badge--{auth/transient/not-found/permission}` on `/admin/import` and `/admin/import-preview` after a real Google Sheets failure | UX-01 | Playwright auto-flow can't reliably trigger live Google API 401/403/404/5xx; visual contrast/spacing of the badge against the surrounding flash content is a 1-screen eyeball check | After `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo`: trigger failures via revoked credentials / nonexistent sheet ID / private sheet without permission; screenshot each badge using `playwright-cli open http://localhost:9090/admin/import` (Desktop + Mobile per CLAUDE.md § Visual Verification) |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 5s for per-task quick run
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-05-21 (all 4 plans shipped; 19/19 task-row statuses ✅; 5 requirements (UX-01, COV-01, CLEAN-01, DOCS-01, BOOK-01) COVERED; visual UAT for UX-01 documented as Manual-Only post-deploy operator action).

---

## Validation Audit 2026-05-21

| Metric | Count |
|--------|-------|
| Requirements audited | 5 (UX-01, COV-01, CLEAN-01, DOCS-01, BOOK-01) |
| Task rows audited | 19 (92-01-01..06, 92-02-01..07, 92-03-01..03, 92-04-01..04) |
| Gaps found | 0 |
| Resolved | n/a (no gaps) |
| Escalated to Manual-Only | 0 |
| Existing Manual-Only entries | 1 (UX-01 4-badge visual verification — post-deploy operator action, recorded pre-audit per STATE.md "Pending UATs UX-01") |
| Phase-level `nyquist_compliant` | `true` |
| State | A (Audit existing — no Test infrastructure rebuild, no auditor agent spawn) |
| Method | Goal-backward audit against `92-{01,02,03,04}-SUMMARY.md` + `92-{01,02,03,04}-VALIDATION.md` shipped-evidence sections (no auditor agent spawned — short-circuit applied per workflow Step 3: "No gaps → skip to Step 6") |

**Verdict:** Phase 92 is Nyquist-compliant. All 5 requirements have automated verification commands; the single Manual-Only entry (UX-01 visual UAT) is documented as post-deploy operator action per STATE.md. No retroactive test generation required. Phase-level `nyquist_compliant: true` confirmed.
