---
phase: 92
slug: carry-forwards-cleanup
status: draft
nyquist_compliant: false
wave_0_complete: false
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
| 92-04-01 | 04 | 1 | DOCS-01 | — | 3 retroactive `89-VERIFICATION.md`, `90-VERIFICATION.md`, `91-VERIFICATION.md` exist under `.planning/milestones/v1.12-phases/{89,90,91}-*/` | file existence | `ls .planning/milestones/v1.12-phases/89-*/89-VERIFICATION.md .planning/milestones/v1.12-phases/90-*/90-VERIFICATION.md .planning/milestones/v1.12-phases/91-*/91-VERIFICATION.md` | ❌ W0 | ⬜ pending |
| 92-04-02 | 04 | 1 | DOCS-01 | — | Each VERIFICATION.md contains 3 required sections (Phase Goal Recap, Goal-Backward Walk-Through, Verification Outcome) | grep | `for f in .planning/milestones/v1.12-phases/{89,90,91}-*/${N}-VERIFICATION.md; do grep -l "Goal Recap" "$f" && grep -l "Goal-Backward" "$f" && grep -l "Verification Outcome" "$f"; done` | ❌ W0 | ⬜ pending |
| 92-04-03 | 04 | 1 | BOOK-01 | — | 7 stale `[ ]` checkboxes flipped to `[x]` | grep count | `[[ "$(grep -c '^- \[ \]' .planning/milestones/v1.12-REQUIREMENTS.md)" == "0" ]]` | ❌ W0 | ⬜ pending |
| 92-04-04 | 04 | 1 | BOOK-01 | — | 4 stale `Pending` rows flipped to `Resolved` | grep count | `[[ "$(grep -c 'Pending' .planning/milestones/v1.12-REQUIREMENTS.md)" == "0" ]]` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/org/ctc/dataimport/CsvImportControllerExceptionTest.java` — extend in-place: add 7 new methods + assert `errorCategory` on existing TransientGoogleApi test (researcher Open Q2 recommends extend-in-place)
- [ ] `src/test/java/org/ctc/admin/controller/RaceControllerCalendarTest.java` — NEW (Plan 92-02)
- [ ] `src/test/java/org/ctc/build/AssumptionsFencePredicateTest.java` — NEW (Plan 92-03, new package `org.ctc.build` — first usage in codebase)
- [ ] `src/test/java/org/ctc/dataimport/GoogleSheetsServiceIT.java` — NEW IT sibling (Plan 92-02; researcher Open Q1 recommends new IT)
- [ ] `src/test/java/org/ctc/dataimport/GoogleCalendarServiceIT.java` — NEW IT sibling (Plan 92-02; researcher Open Q1 recommends new IT)
- [ ] No new framework — JUnit 5, Mockito, AssertJ, Spring Boot Test, MockMvc all already on classpath

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Plan 92-01 visual rendering of `.error-badge--{auth/transient/not-found/permission}` on `/admin/import` and `/admin/import-preview` after a real Google Sheets failure | UX-01 | Playwright auto-flow can't reliably trigger live Google API 401/403/404/5xx; visual contrast/spacing of the badge against the surrounding flash content is a 1-screen eyeball check | After `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo`: trigger failures via revoked credentials / nonexistent sheet ID / private sheet without permission; screenshot each badge using `playwright-cli open http://localhost:9090/admin/import` (Desktop + Mobile per CLAUDE.md § Visual Verification) |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 5s for per-task quick run
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending (set once Phase 92 plans are finalized and per-plan VALIDATION.md derivations are in place)
