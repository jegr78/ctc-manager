---
phase: 92
plan: 02
slug: carry-forwards-cleanup
status: shipped
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-21
---

# Plan 92-02 — Validation Slice

> Per-plan slice of `92-VALIDATION.md` per CONTEXT D-08.
> 7 rows 92-02-01..07 covering COV-01 JaCoCo recovery via `RaceControllerCalendarTest`
> + 2 new IT siblings + extra happy-path coverage on `CsvImportControllerExceptionTest`.

---

## Sampling Rate

- **Per-task command (after Task 1, unit gate):** `./mvnw test -Dtest='RaceControllerCalendarTest'` (~32 s, 9 methods)
- **Per-plan command (Task 3, full gate):** `./mvnw verify` (~7:48 min, 1450+ tests + JaCoCo + SpotBugs)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 92-02-01 | 02 | 1 | COV-01 | — | RaceController GET `/admin/races/{id}` — `calendarAvailable` model attribute set | unit (MockMvc standalone-noop-resolver) | `./mvnw test -Dtest='RaceControllerCalendarTest#givenCalendarAvailable_whenGetRaceDetail_thenModelHasCalendarAvailableTrue'` | ✅ | ✅ green |
| 92-02-02 | 02 | 1 | COV-01 | — | RaceController GET — `hasCalendarEvent` + `canCreateCalendarEvent` branches | unit (MockMvc standalone) | 2 `@Test` methods on `RaceControllerCalendarTest` | ✅ | ✅ green |
| 92-02-03 | 02 | 1 | COV-01 | — | RaceController POST `/create-calendar-event` — 4 typed-catch arms (AUTH/NOT_FOUND/PERMISSION/TRANSIENT) | unit (MockMvc) | 4 `@Test` methods on `RaceControllerCalendarTest` | ✅ | ✅ green |
| 92-02-04 | 02 | 1 | COV-01 | — | RaceController POST — `IllegalStateException` catch path | unit (MockMvc) | `givenCalendarIllegalState_whenPostCreateCalendarEvent_thenRedirectsWithPlainMessage` | ✅ | ✅ green |
| 92-02-05 | 02 | 1 | COV-01 | — | `GoogleApiExceptionMapper` IOException → `TransientGoogleApiException` default mapping (real Google client; @SpringBootTest IT) | IT (`@SpringBootTest`+`@Tag("integration")`) | `./mvnw verify -Dit.test='GoogleSheetsServiceIT'` | ✅ | ✅ green |
| 92-02-06 | 02 | 1 | COV-01 | — | `GoogleApiExceptionMapper` 403 reason `authError` → `AuthGoogleApiException` (not Permission); GeneralSecurityException → `AuthGoogleApiException` | IT | `./mvnw verify -Dit.test='GoogleCalendarServiceIT'` | ✅ | ✅ green |
| 92-02-07 | 02 | 1 | COV-01 | — | JaCoCo line coverage ≥ 88.88 % (v1.11 baseline restoration) | gate | `./mvnw verify` + header-validated awk on `target/site/jacoco/jacoco.csv` | ✅ | ✅ green (88.8838 %) |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `src/test/java/org/ctc/admin/controller/RaceControllerCalendarTest.java` — NEW: 9 `@Test` methods (4 typed-catch + IllegalState + happy + 3 GET model attributes via standalone noop view-resolver)
- [x] `src/test/java/org/ctc/dataimport/GoogleSheetsServiceIT.java` — NEW IT sibling: 3 `@Test` methods covering IOException default + 404 GoogleJsonResponseException + IOException on readRange
- [x] `src/test/java/org/ctc/dataimport/GoogleCalendarServiceIT.java` — NEW IT sibling: 3 `@Test` methods covering 403/authError → AuthGoogleApiException, 403/forbidden → PermissionGoogleApiException, GeneralSecurityException → AuthGoogleApiException
- [x] `src/test/java/org/ctc/dataimport/CsvImportControllerExceptionTest.java` — extended in place with 4 additional happy-path tests (preview-sheet success, empty-raceSheets, scorecard-parser-empty, no-file-or-sheet) to close JaCoCo gap

---

## W4 / W5 Disposition

- **W4 (header validation):** JaCoCo CSV columns 8/9 confirmed `LINE_MISSED`/`LINE_COVERED` (header-guard awk exit 0).
- **W5 (defensive sealed-base catch arm handling):** Organic recompute reached 88.8838 % ≥ 88.88 % without requiring a JaCoCo `<exclude>` rule. The defensive `catch (GoogleApiException e)` arms in `CsvImportController`, `DriverSheetImportController`, and `RaceController` remain technically uncovered (sealed permits forbid external subclassing — physical unreachability) but the cumulative organic line gain from `RaceControllerCalendarTest` + 2 ITs + 4 extra happy-path tests on `CsvImportControllerExceptionTest` more than compensated. No pom.xml change required.

---

## W9 Pre-flight Grep Outcome

`grep -n "private final" src/main/java/org/ctc/admin/controller/RaceController.java` returned 5 collaborators matching the reflective list (no drift):
- `RaceService`
- `RaceFormDataService`
- `RaceCalendarService`
- `RaceAttachmentService`
- `RaceGraphicService`

All 5 declared as `@MockitoBean` in `RaceControllerCalendarTest`.

---

## Sign-Off

| Field | Value |
|-------|-------|
| Shipper | gsd-executor (inline, sequential) |
| Ship date | 2026-05-21 |
| Commit SHA short | `84cae1dd` |
| `./mvnw verify` exit code | 0 (BUILD SUCCESS, 7:48 min) |
| Tests run | 1452 (Failures: 0, Errors: 0; +14 new tests vs Plan 92-01 ship state — 9 RaceControllerCalendarTest + 3 GoogleSheetsServiceIT + 3 GoogleCalendarServiceIT + 4 extra Csv happy-path; net Δ accounts for 1 augmented + 1 renamed CsvImportControllerExceptionTest method) |
| JaCoCo line coverage | **88.8838 %** (covered=7700, missed=963) — v1.11 baseline restored (≥ 88.88 %); Δ +0.44 pp over v1.12 (88.44 %) |
| SpotBugs BugInstance count | 0 |
| `git diff --stat src/main/` | empty (test-only plan per CONTEXT D-10) |
| `nyquist_compliant` | `true` |
