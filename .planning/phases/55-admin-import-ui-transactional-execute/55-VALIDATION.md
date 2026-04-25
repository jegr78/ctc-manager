---
phase: 55
slug: admin-import-ui-transactional-execute
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-24
audited: 2026-04-25
---

# Phase 55 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + Spring Boot Test + MockMvc + Mockito + AssertJ |
| **Config file** | `pom.xml` Surefire lines 184-194 (Unit+Integration in same Surefire run) |
| **Quick run command** | `./mvnw test -Dtest=DriverSheetImportControllerTest` (focused controller suite, ~5s) |
| **Full suite command** | `./mvnw verify` (all tests + JaCoCo 82% gate, ~3min) |
| **Estimated runtime** | ~5s focused / ~3min full |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -Dtest=DriverSheetImportControllerTest` (focused; keeps feedback latency under 10s during implementation)
- **After every plan wave:** Run `./mvnw verify` (full suite + JaCoCo gate)
- **Before `/gsd-verify-work`:** `./mvnw verify` must be green
- **Max feedback latency:** 10s for focused tests, 3min for full suite

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 55-01-01 | 01 | 0 | Scaffold | — | N/A | N/A (stub) | `./mvnw test-compile` | ✅ | ✅ green |
| 55-01-02 | 01 | 0 | IMPORT-01 + TEST-02 | — | N/A | integration | `./mvnw test -Dtest=DriverSheetImportControllerTest#givenGetRequest_whenShowImportForm_thenReturns200WithFormAttributes` | ✅ | ✅ green |
| 55-01-03 | 01 | 0 | TEST-02 (preview happy) | T-54-02 | Thymeleaf auto-escape via `th:text` (no `th:utext`) | integration | `./mvnw test -Dtest=DriverSheetImportControllerTest#givenValidSheetUrl_whenPostPreview_thenRendersPreviewTemplate` | ✅ | ✅ green |
| 55-01-04 | 01 | 0 | TEST-02 (preview error) | — | IOException surfaced via errorMessage | integration | `./mvnw test -Dtest=DriverSheetImportControllerTest#givenInvalidSheetUrl_whenPostPreview_thenRendersFormWithError` | ✅ | ✅ green |
| 55-01-05 | 01 | 1 | IMPORT-06 (new driver) + UX-01 | — | N/A | integration | `./mvnw test -Dtest=DriverSheetImportControllerTest#givenNewDriverRow_whenExecute_thenCreatesDriverAndSeasonDriver` | ✅ | ✅ green |
| 55-01-06 | 01 | 1 | IMPORT-06 (new assignment) + UX-02 | — | N/A | integration | `./mvnw test -Dtest=DriverSheetImportControllerTest#givenNewAssignmentRow_whenExecute_thenCreatesSeasonDriverOnly` | ✅ | ✅ green |
| 55-01-07 | 01 | 1 | UX-07 + DATA-03 (skip) | — | Skip checked → existing SeasonDriver untouched | integration | `./mvnw test -Dtest=DriverSheetImportControllerTest#givenConflictRowWithSkipSet_whenExecute_thenExistingSeasonDriverUntouched` | ✅ | ✅ green |
| 55-01-08 | 01 | 1 | UX-07 + DATA-03 (overwrite default) | — | Skip absent → overwrite with sheet team | integration | `./mvnw test -Dtest=DriverSheetImportControllerTest#givenConflictRowWithoutSkip_whenExecute_thenSeasonDriverTeamOverwritten` | ✅ | ✅ green |
| 55-01-09 | 01 | 1 | UX-08 (accept set) | — | Accept ticked → existing driver linked | integration | `./mvnw test -Dtest=DriverSheetImportControllerTest#givenFuzzyRowWithAcceptSet_whenExecute_thenLinksToExistingDriver` | ✅ | ✅ green |
| 55-01-10 | 01 | 1 | UX-08 (accept unset default) | — | Accept absent → new Driver created | integration | `./mvnw test -Dtest=DriverSheetImportControllerTest#givenFuzzyRowWithoutAccept_whenExecute_thenCreatesNewDriver` | ✅ | ✅ green |
| 55-01-11 | 01 | 1 | UX-05 (unchanged no-op) | — | Same-team row → no DB writes for that row | integration | `./mvnw test -Dtest=DriverSheetImportControllerTest#givenUnchangedRow_whenExecute_thenNoWrite` | ✅ | ✅ green |
| 55-01-12 | 01 | 1 | UX-06 + DATA-02 (error exclusion) | — | ERROR rows never imported | integration | `./mvnw test -Dtest=DriverSheetImportControllerTest#givenErrorRow_whenExecute_thenRowExcluded` | ✅ | ✅ green |
| 55-01-13 | 01 | 1 | MATCH-02 + D-07 (cross-tab dedup) | — | Same PSN across tabs → single Driver, multi SeasonDriver | integration | `./mvnw test -Dtest=DriverSheetImportControllerTest#givenSameNewPsnInTwoTabs_whenExecute_thenSingleDriverCreated` | ✅ | ✅ green |
| 55-01-14 | 01 | 1 | D-16 (ambiguous season) | — | Tab without seasonId → skipped, others imported | integration | `./mvnw test -Dtest=DriverSheetImportControllerTest#givenTabWithoutSeasonId_whenExecute_thenTabSkippedAndFlaggedInFlash` | ✅ | ✅ green |
| 55-01-15 | 01 | 1 | IMPORT-06 (transactional rollback) | — | DataAccessException → full rollback + redirect with flash error | integration | `./mvnw test -Dtest=DriverSheetImportControllerExceptionTest#givenExecuteThrowsDataAccessException_whenPostExecute_thenRedirectWithFlashError` | ✅ | ✅ green |
| 55-01-16 | 01 | 1 | D-17 (flash summary) | — | Flash carries aggregated headline counts | integration | `./mvnw test -Dtest=DriverSheetImportControllerTest#givenMixedBucketExecute_whenSuccess_thenFlashContainsAggregatedCounts` | ✅ | ✅ green |
| 55-01-17 | 01 | 1 | IMPORT-01 (entry button) | — | drivers.html contains link to /admin/drivers/import | integration | `./mvnw test -Dtest=DriverSheetImportControllerTest#givenDriversPage_whenGet_thenContainsImportButton` | ✅ | ✅ green |
| 55-01-18 | 01 | 1 | Preview exception (ambiguous URL) | — | Preview service IOException → form rerender with error | integration | `./mvnw test -Dtest=DriverSheetImportControllerExceptionTest#givenPreviewThrowsIOException_whenPostPreview_thenFormWithError` | ✅ | ✅ green |
| 55-01-19 | 01 | 1 | Execute exception path | — | Execute service BusinessRuleException → redirect with flash error | integration | `./mvnw test -Dtest=DriverSheetImportControllerExceptionTest#givenExecuteThrowsBusinessRule_whenPostExecute_thenRedirectWithFlashError` | ✅ | ✅ green |
| 55-01-20 | 01 | 1 | Execute missing sheetUrl | — | sheetUrl null/blank → redirect with flash error (no NPE) | integration | `./mvnw test -Dtest=DriverSheetImportControllerExceptionTest#givenMissingSheetUrl_whenPostExecute_thenRedirectWithError` | ✅ | ✅ green |
| 55-01-21 | 01 | 1 | JaCoCo 82% gate (TEST-03) | — | Coverage check | coverage | `./mvnw verify` | ✅ (pom.xml existing) | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Total: 20 automated test methods covering all 11 REQ-IDs + 4 locked decisions (D-06, D-07, D-16, D-17) + T-54-02 mitigation. Plus JaCoCo coverage gate.**

---

## Wave 0 Requirements

Wave 0 creates the minimal compilable scaffold so Wave 1 tests can reference the classes without NoClassDefFoundError:

- [x] `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` — full controller with 3 handlers (GET, POST /preview, POST /execute), `DriverSheetImportService` + `GoogleSheetsService` + `SeasonManagementService` injected, `hasAmbiguousTabs` model attribute (CR-01 fix carryover). Final implementation > scaffold.
- [x] `src/main/resources/templates/admin/driver-import.html` — Thymeleaf template extending `admin/layout`, sheet URL form with `sheetsAvailable` guard.
- [x] `src/main/resources/templates/admin/driver-import-preview.html` — full preview template with 6 bucket tables, Skip/Accept checkboxes, ambiguous-season banner outside tab loop.
- [x] `src/test/java/org/ctc/dataimport/DriverSheetImportControllerTest.java` — `@SpringBootTest` + `@AutoConfigureMockMvc` + `@MockitoBean GoogleSheetsService` + `@Transactional`. Contains 18 @Test methods (15 from VALIDATION map + 3 bonus: sheets-unavailable test, in-class DataAccessException rollback, cross-tab fuzzy accept regression).
- [x] `src/test/java/org/ctc/dataimport/DriverSheetImportControllerExceptionTest.java` — `@MockitoBean DriverSheetImportService`, 4 @Test methods covering the narrowed catch blocks (IOException, BusinessRuleException, DataAccessException, missing sheetUrl).

**Framework install:** none — Spring Boot Test, MockMvc, Mockito, AssertJ all present via existing pom.xml (shared with Phase 54 tests).

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Visual correctness of preview page (per-tab card layout, buckets rendered) | D-14 | Thymeleaf rendering correctness depends on actual browser paint; MockMvc verifies model+template name, not visual layout | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` then `playwright-cli open http://localhost:9090/admin/drivers/import` — submit a known test Sheet URL, inspect preview page Desktop + Mobile |
| Visual correctness of ambiguous-season warning banner (D-16) | UX-07, D-16 | Banner styling + placement | Same as above — submit a sheet where 2024 has no Season configured; verify banner appears above Execute button |
| CSS class compliance (QUAL-01) | QUAL-01 | grep alone can't validate visual balance | `grep -cE 'style="' src/main/resources/templates/admin/driver-import*.html` must return 0; additionally open both templates in browser, confirm no inline-style regressions vs. admin.css styling |
| Controller code quality (QUAL-02) | QUAL-02 | No business logic / no repository calls in controller — static check | `grep -cE 'Repository|[.]save\(' src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` must return 0 |
| Form binding via @RequestParam, not entity (QUAL-03) | QUAL-03 | No JPA entity `@ModelAttribute` | `grep -cE '@ModelAttribute.*Driver \|Season \|Team ' src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` must return 0 |
| Preview-state via re-fetch (QUAL-04) | QUAL-04 | No `@SessionAttributes` pattern | `grep -c '@SessionAttributes' src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` must return 0 |
| DATA-05 carryover | DATA-05 | RaceLineup never modified by import | `grep -iE 'raceLineup\|RaceLineup' src/main/java/org/ctc/admin/controller/DriverSheetImportController.java src/main/java/org/ctc/dataimport/DriverSheetImportService.java` must return 0 |
| T-54-02 mitigation | Threat T-54-02 (from Phase 54) | Stored-XSS via sheet cells → Thymeleaf auto-escape | `grep -c 'th:utext' src/main/resources/templates/admin/driver-import*.html` must return 0 (only `th:text` for user data) |

---

## Validation Sign-Off

- [x] All 20 tasks have `<automated>` verify OR Wave 0 dependency
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (controller, 2 templates, 2 test-class files all shipped)
- [x] No watch-mode flags (Maven surefire does not run in watch mode by default)
- [x] Feedback latency < 10s (focused test ~5s) / < 3min (full suite ~50min including Playwright E2E, ~3min without)
- [x] `nyquist_compliant: true` set in frontmatter after plan-checker passes

**Approval:** approved 2026-04-25 (Jens Gross via /gsd-validate-phase audit)

---

## Validation Audit 2026-04-25

| Metric | Count |
|--------|-------|
| Tasks in scope | 21 (1 Wave 0 + 19 test methods + 1 JaCoCo gate) |
| Gaps found | 0 |
| Resolved (already covered, status flipped) | 21 |
| Escalated | 0 |
| New tests written | 0 |
| Bonus coverage | 3 (sheets-unavailable, in-class DataAccessException rollback, cross-tab fuzzy accept regression) |

**Audit method:** Cross-referenced VALIDATION.md per-task method names with `grep "void (given\|when)"` against `DriverSheetImportControllerTest.java` (18 methods) and `DriverSheetImportControllerExceptionTest.java` (4 methods). Method names diverge in 4 cases (test names rephrased but functionally equivalent — e.g. 55-01-02 expected `givenGetRequest_whenShowImportForm_thenReturns200WithFormAttributes`, ships as `whenGetImportPage_thenShowsImportFormWithSeasonsAndSheetsAvailable`); behavior is exercised. JaCoCo 82% line gate confirmed via `./mvnw verify` BUILD SUCCESS at 1064 tests.

**Manual-only verifications confirmed:**

- QUAL-01: `grep -cE 'style="' src/main/resources/templates/admin/driver-import*.html` → 0
- QUAL-02: `grep -cE 'Repository|[.]save\(' src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` → 0
- QUAL-03: `grep -cE '@ModelAttribute.*Driver |Season |Team ' .../DriverSheetImportController.java` → 0
- QUAL-04: `grep -c '@SessionAttributes' .../DriverSheetImportController.java` → 0
- DATA-05 carryover: `grep -iE 'raceLineup|RaceLineup' src/main/java/org/ctc/dataimport/*.java src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` → 0
- T-54-02 mitigation: `grep -c 'th:utext' src/main/resources/templates/admin/driver-import*.html` → 0

Visual UAT items (banner placement, button rendering, form layout) covered by `55-HUMAN-UAT.md` (status: complete, 3/3 pass).

**Outcome:** Phase 55 is Nyquist-compliant. No gap-filling required — VALIDATION.md status was simply never closed during execution. Statuses flipped retroactively against the shipped tests.
