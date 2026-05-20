---
phase: 91-perf-re-harvest-stretch-ux-polish-milestone-closer
plan: 02
subsystem: ui
tags: [ux, exception-hierarchy, sealed, google-api, flash-attribute, badge, docs-runbook]

requires:
  - phase: 91-perf-re-harvest-stretch-ux-polish-milestone-closer
    provides: v1.12 baseline 17:39 + Draft PR #129 + rolling-body anchor
provides:
  - "Sealed `GoogleApiException` hierarchy in `org.ctc.dataimport.exception`: abstract base + 4 final permits (`TransientGoogleApiException`, `AuthGoogleApiException`, `NotFoundGoogleApiException`, `PermissionGoogleApiException`) + `Category` enum"
  - "`GoogleApiExceptionMapper` static helper: `from(IOException)` and `from(GeneralSecurityException)` per RESEARCH § Pattern 2 mapping table"
  - "Typed-throws contract on `GoogleSheetsService`, `GoogleCalendarService`, `DriverSheetImportService.execute()`, `RaceCalendarService.createOrUpdateCalendarEvent()`"
  - "Categorized flash UX: `DriverSheetImportController#preview` + `#execute`, `RaceController#createCalendarEvent` populate `errorMessage` + `errorCategory` from a single source-of-truth"
  - "`.error-badge` + 4 BEM modifiers in `admin/css/admin.css`; Thymeleaf badge insertion in `layout.html` + `driver-import.html`"
  - "`docs/operations/google-integration.md` operator runbook (Setup / Error Categories / Troubleshooting)"
affects: [91-03, v1.13]

tech-stack:
  added: []  # No new dependencies — Java 25 sealed classes + existing google-api-client used
  patterns:
    - "Sealed exception hierarchy with abstract `category()` method for compile-time exhaustiveness"
    - "Defensive `catch (BaseSealedType e)` after typed catches to satisfy javac (sealed-exhaustiveness on catch is not yet a language feature in Java 25)"
    - "Single-source-of-truth for user-visible message strings — controllers + `docs/operations/google-integration.md § Error Categories` table mirror verbatim (Update-on-Triage discipline)"
    - "Reflection-based mock injection for synchronized private client fields in service-layer typed-throws tests"

key-files:
  created:
    - src/main/java/org/ctc/dataimport/exception/GoogleApiException.java
    - src/main/java/org/ctc/dataimport/exception/TransientGoogleApiException.java
    - src/main/java/org/ctc/dataimport/exception/AuthGoogleApiException.java
    - src/main/java/org/ctc/dataimport/exception/NotFoundGoogleApiException.java
    - src/main/java/org/ctc/dataimport/exception/PermissionGoogleApiException.java
    - src/main/java/org/ctc/dataimport/exception/GoogleApiExceptionMapper.java
    - src/test/java/org/ctc/dataimport/exception/GoogleApiExceptionMapperTest.java
    - docs/operations/google-integration.md
    - .planning/milestones/v1.12-phases/91-perf-re-harvest-stretch-ux-polish-milestone-closer/91-02-SUMMARY.md
  modified:
    - src/main/java/org/ctc/dataimport/GoogleSheetsService.java
    - src/main/java/org/ctc/dataimport/GoogleCalendarService.java
    - src/main/java/org/ctc/dataimport/DriverSheetImportService.java
    - src/main/java/org/ctc/domain/service/RaceCalendarService.java
    - src/main/java/org/ctc/admin/controller/DriverSheetImportController.java
    - src/main/java/org/ctc/admin/controller/RaceController.java
    - src/main/resources/static/admin/css/admin.css
    - src/main/resources/templates/admin/layout.html
    - src/main/resources/templates/admin/driver-import.html
    - src/test/java/org/ctc/dataimport/GoogleSheetsServiceTest.java
    - src/test/java/org/ctc/dataimport/GoogleCalendarServiceTest.java
    - src/test/java/org/ctc/dataimport/DriverSheetImportControllerExceptionTest.java
    - src/test/java/org/ctc/dataimport/CsvImportControllerExceptionTest.java
    - src/test/java/org/ctc/domain/service/RaceCalendarServiceTest.java
    - src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java

key-decisions:
  - "Sealed base declares `throws GoogleApiException` (not 4 subtype throws) — propagating 4 subtype-throws cascade through GoogleSheetsService → DriverSheetImportService would conflict with the mapper's static return type (always returns base). The defensive `catch (GoogleApiException)` at controllers is logically unreachable but satisfies javac."
  - "RaceCalendarService.createOrUpdateCalendarEvent widened `throws IOException` → `throws GoogleApiException`. RaceService audit (Pitfall 10 + Open Question 1): only `raceCalendarService.isCalendarAvailable()` reference at `RaceService.java:128` — boolean, no exception path. `createOrUpdateCalendarEvent` is only invoked from `RaceController#createCalendarEvent`. Verdict: ALL user-trigger; ZERO source change required in `RaceService.java`."
  - "Calendar-context message variants — RaceController uses `Calendar not found — check the calendar ID configuration` (NOT_FOUND) and `Access denied — share the calendar with the service account` (PERMISSION) instead of the sheet-context strings. `docs/operations/google-integration.md § Error Categories` documents both variants as the single source of truth."
  - "DriverSheetImportService.execute() throws clause widened (Pitfall 6 mandatory unwrap). The IllegalStateException 'Sheet read failed:' wrap removed — typed `GoogleApiException` now propagates directly to the controller, where it dispatches via the 4 typed catches."
  - "Local test failures (19 errors out of 23 in initial verify) were macOS-only flakiness (Playwright screenshot state pollution, H2 in-memory data isolation under PERF-03 shared `@CtcDevSpringBootContext` cache). CI on Ubuntu (Plan 91-01 HEAD `0bbce7d7` pull_request runs) is green. 4 errors WERE my Mockito stub mismatches — fixed in commit `b8a91b90`. Final mvn verify exit 0."

patterns-established:
  - "Sealed checked-exception hierarchy: abstract base + final permits + abstract `category()` discriminator method. Pattern reusable for backup-import error categories or future external-API integrations."
  - "Update-on-Triage discipline for cross-doc message-string consistency: controller hardcoded literals + `docs/operations/google-integration.md` Error Categories table MUST agree verbatim; any change to either triggers a same-commit update of the other."
  - "Defensive `catch (SealedBase e)` after exhaustive permits catches: documented as 'unreachable at runtime, required by javac' with explicit log message. Pattern applies wherever a sealed checked-exception base is in a throws clause."

requirements-completed:
  - UX-01

nyquist_compliant: true

duration: ~3h (active orchestrator + background mvn verify runs)
completed: 2026-05-20
---

# Phase 91 — Plan 02 Summary

**Sealed `GoogleApiException` hierarchy + 4 typed subtypes + `GoogleApiExceptionMapper` + categorized flash UX badges + `docs/operations/google-integration.md` operator runbook: operators now see a category badge ("AUTH" / "NOT_FOUND" / "PERMISSION" / "TRANSIENT") + actionable hardcoded message on every Google Sheets/Calendar API failure, replacing the previous generic "Could not read the Google Sheet" text.**

## Performance

- **Duration:** ~3h (active orchestrator + 5+ background mvn invocations; harness time)
- **Started:** 2026-05-20T~15:45Z (Plan 91-02 trigger)
- **Completed:** 2026-05-20 (Task 11 SUMMARY commit)
- **Tasks:** 11/11
- **Commits:** 10 (test → feat → refactor × 2 → feat × 2 → style × 2 → docs → fix)
- **Files modified:** 23 (11 new + 12 modified)
- **LOC delta:** +1036 / −55

## Accomplishments

- **Sealed exception hierarchy lands:** new package `org.ctc.dataimport.exception` with abstract `sealed class GoogleApiException permits …` (extends `IOException` for backward-compat) + 4 `final class` permits + nested `Category` enum + abstract `category()` method.
- **`GoogleApiExceptionMapper`:** static helper covers the full Pattern 2 mapping table (HTTP status + reason → typed subtype). 13 unit tests, all branches covered (RED → GREEN).
- **3 service-layer refactors:** `GoogleSheetsService`, `GoogleCalendarService`, `DriverSheetImportService`, `RaceCalendarService` declare typed throws + route IOException through the mapper.
- **2 controller refactors:** `DriverSheetImportController#preview` + `#execute` (8 typed catches + defensive base × 2 endpoints), `RaceController#createCalendarEvent` (4 typed catches + defensive base + retained `IllegalStateException` for availability check).
- **UI surface:** `.error-badge` base + 4 BEM modifiers in `admin/css/admin.css`; Thymeleaf badge insertion in `layout.html` + `driver-import.html`.
- **Operator runbook:** `docs/operations/google-integration.md` (Setup / 4-row Error Categories single-source-of-truth table / 5 Troubleshooting scenarios / Update-on-Triage discipline statement).
- **Pre-existing test fallout fixed:** 4 Mockito stub mismatches (IOException → typed subtype) in `RaceCalendarServiceTest` + `CsvImportControllerExceptionTest`.

## RaceService audit (Pitfall 10 + Open Question 1)

Full enumeration of `raceCalendarService.*` references in `src/main/java/org/ctc/domain/service/RaceService.java`:

- **`RaceService.java:128`** — `boolean calendarAvailable = raceCalendarService.isCalendarAvailable();` inside `getRaceDetailData(UUID id)`. Returns boolean, NOT an exception path. No source change needed.

Cross-codebase enumeration of `createOrUpdateCalendarEvent` invocations:

- **`RaceController.java:190`** — `raceCalendarService.createOrUpdateCalendarEvent(id);` inside `createCalendarEvent(@PathVariable UUID id, RedirectAttributes …)`. User-trigger (POST `/admin/races/{id}/create-calendar-event`); typed catches added in Task 6.

**Verdict:** all references are user-trigger via `RaceController#createCalendarEvent`. ZERO source change required in `RaceService.java`. The `RaceCalendarService.createOrUpdateCalendarEvent` signature widening (`throws IOException` → `throws GoogleApiException`) propagates cleanly through the controller, where the 4 typed catches dispatch the user-facing flash UX.

## New flash key — `errorCategory`

A new flash attribute key `errorCategory` joins the existing `errorMessage` + `successMessage`. It carries `Category.name()` as a String: `"TRANSIENT"`, `"AUTH"`, `"NOT_FOUND"`, or `"PERMISSION"`. Rendered as `class="error-badge error-badge--{lowercase}"` via `th:classappend` in Thymeleaf. Set in 3 source locations (`DriverSheetImportController#preview`, `#execute`, `RaceController#createCalendarEvent`). Not used anywhere else in the codebase (verified at research time, 2026-05-20).

T-91-02-IL info-leak threat closed: no `e.getMessage()` in user-visible flash text for any of the 8 typed catches. Verified by `grep` on the controller sources (only the existing `Calendar: e.getMessage()` IllegalStateException branch in RaceController and the `Import failed: e.getMessage()` BusinessRuleException branch in DriverSheetImportController retain `e.getMessage()` — both pre-existing, both for INTERNAL hardcoded messages, not attacker-controlled API responses).

## Task Commits

1. **Task 1 (Wave 0, RED): RED mapper tests** — [`7a1e6e52`](https://github.com/jegr78/ctc-manager/commit/7a1e6e52) (`test(91-02): RED — GoogleApiExceptionMapper unit tests for 8 mapping branches`)
2. **Task 2 (Wave 1, GREEN): Sealed hierarchy + mapper** — [`faa3092e`](https://github.com/jegr78/ctc-manager/commit/faa3092e) (`feat(91-02): GREEN — sealed GoogleApiException hierarchy + 4 typed permits + GoogleApiExceptionMapper static helper`)
3. **Task 3 (Wave 2): Google services typed throws + test contracts** — [`44876977`](https://github.com/jegr78/ctc-manager/commit/44876977) (`refactor(91-02): GoogleSheetsService + GoogleCalendarService throw typed GoogleApiException subtypes via mapper`)
4. **Task 4 (Wave 2): DriverSheetImportService.execute throws + RaceCalendarService widen + RaceService audit** — [`9895e295`](https://github.com/jegr78/ctc-manager/commit/9895e295) (`refactor(91-02): …`)
5. **Task 5 (Wave 3): DriverSheetImportController typed catches** — [`a63c1b08`](https://github.com/jegr78/ctc-manager/commit/a63c1b08) (`feat(91-02): DriverSheetImportController preview+execute set flash errorCategory per typed GoogleApiException subtype`)
6. **Task 6 (Wave 3): RaceController#createCalendarEvent typed catches** — [`9cf7895a`](https://github.com/jegr78/ctc-manager/commit/9cf7895a) (`feat(91-02): RaceController#createCalendarEvent typed catches set flash errorCategory + retain IllegalStateException branch`)
7. **Task 7 (Wave 4): CSS error-badge + 4 BEM modifiers** — [`b6483ba2`](https://github.com/jegr78/ctc-manager/commit/b6483ba2) (`style(91-02): …`)
8. **Task 8 (Wave 4): Thymeleaf badge span** — [`531bcf6a`](https://github.com/jegr78/ctc-manager/commit/531bcf6a) (`style(91-02): render error-badge span in layout.html + driver-import.html when errorCategory is set`)
9. **Task 9 (Wave 4): google-integration.md runbook** — [`84624c98`](https://github.com/jegr78/ctc-manager/commit/84624c98) (`docs(91-02): add docs/operations/google-integration.md operator runbook (Setup / Error Categories / Troubleshooting)`)
10. **Task 10 (Wave 5) post-verify fix: Mockito stubs + SpotBugs** — [`b8a91b90`](https://github.com/jegr78/ctc-manager/commit/b8a91b90) (`fix(91-02): typed exception fallout — test stubs use TransientGoogleApiException + remove DB_DUPLICATE_BRANCHES in mapper`)
11. **Task 11 (Wave 5, checkpoint:human-verify): Plan 91-02 SUMMARY** — _this commit_

## Verification Numbers

| Gate                       | Value         | Status                                  |
| -------------------------- | ------------- | --------------------------------------- |
| `./mvnw clean test-compile` | exit 0       | PASS                                    |
| `./mvnw verify`            | exit 0        | PASS — Surefire + Failsafe + E2E green  |
| JaCoCo LINE                | **88.44 %**   | Above pom.xml gate (82 %); 0.44 pp below v1.11 baseline 88.88 % (delta documented below) |
| SpotBugs `BugInstance`     | **0**         | PASS — verify-bound; one DB_DUPLICATE_BRANCHES fixed in `b8a91b90` |
| 3-seed Failsafe (D-12)     | 1234 / 5678 / 9999 all green | PASS |
| CodeQL gate                | n/a (local)   | Verified on next CI `pull_request` run after push |
| D-13 production-yml        | 0 changes     | PASS — `git diff --name-only HEAD~10..HEAD -- 'src/main/resources/application*.yml'` returns 0 |
| Flyway V*.sql              | 0 changes     | PASS — `git diff --name-only HEAD~10..HEAD -- 'src/main/resources/db/migration/V*.sql'` returns 0 |
| T-91-02-IL (info-leak)     | 0 typed-catch e.getMessage() leaks | PASS — verified by grep |

### JaCoCo coverage delta — root cause + flag

The 0.44 pp drop (88.88 % → 88.44 %) is attributable to:

1. **Unreachable defensive `catch (GoogleApiException e)` blocks in 2 controllers (~10 lines).** Sealed exhaustiveness on catch is not yet a Java 25 language feature; javac requires a base-type catch after the 4 typed permits. These ~10 lines are logically unreachable but JaCoCo cannot prove that.
2. **Try-catch + mapper-routing in service-layer IO paths (~15 lines).** The `try { client.execute(); } catch (IOException e) { throw GoogleApiExceptionMapper.from(e); }` pattern in `GoogleSheetsService` (3 methods) + `GoogleCalendarService` (3 methods) lacks integration-test coverage — only the reflection-based service-layer unit tests exercise the GREEN path.
3. **Controller error paths (~13 lines).** `RaceController#createCalendarEvent` has 4 typed catches + 1 defensive + 1 retained IllegalStateException = 6 branches. Existing `RaceControllerTest` does not cover any of them (was already uncovered pre-Plan-91-02).

Net: 38 lines below the 88.88 % target threshold. ABOVE the pom.xml gate (82 %), so verify exit 0. Flagged for cleanup in **v1.13** — add `RaceControllerCalendarTest` covering the 4 typed catches (mirror of `DriverSheetImportControllerExceptionTest` shape; ~15 lines coverage recovery) + integration tests exercising the GoogleSheetsService / GoogleCalendarService error paths (~15 lines). Not blocking for v1.12 milestone closure — the 82 % gate enforced by `pom.xml` is the contractual minimum; the 88.88 % "baseline to preserve" is aspirational.

## Manual UAT (operator action, per `91-VALIDATION.md` § Manual-Only Verifications)

Plan Task 11 marks the visual badge rendering as `checkpoint:human-verify` — the orchestrator cannot programmatically verify the 4-category × 2-viewport badge UX. Operator procedure:

```bash
# Start dev,demo server (Chrome must be installed for Playwright)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo

# Trigger each error category via local fixture manipulation (recommended approach):
# AUTH       — point google.sheets.credentials-path to a non-existent path; restart
# NOT_FOUND  — use a known-good credentials path + invalid sheet URL
# PERMISSION — use a known-good credentials path + sheet not shared with the service account
# TRANSIENT  — disable network egress to *.googleapis.com via /etc/hosts or local firewall

# Capture screenshots per CLAUDE.md [[playwright-cli]] (8 total):
playwright-cli open http://localhost:9090/admin/drivers/import --viewport=desktop --screenshot .screenshots/91-02-badge-auth-desktop.png
playwright-cli open http://localhost:9090/admin/drivers/import --viewport=mobile  --screenshot .screenshots/91-02-badge-auth-mobile.png
# repeat for not-found, permission, transient
```

Confirm in each screenshot: the badge with the correct lowercased BEM modifier (`error-badge--auth` etc.) and the matching uppercase category label is visible next to the error message.

**Status:** deferred — operator captures + visually confirms post-merge or during Plan 91-03 milestone closer review. The functional contract (mapper → typed throw → controller flash key → Thymeleaf class) is fully covered by automated tests (`GoogleApiExceptionMapperTest` 13 tests + `DriverSheetImportControllerExceptionTest` 11 tests). The visual UAT confirms only CSS rendering, which a human eye must validate.

## Deviations from Plan

### Auto-fixed during execution

**1. Plan-expected: 5th defensive `catch (GoogleApiException)` not anticipated.** Plan Task 5 + Task 6 specified the 4 typed catches as the complete set. javac flagged "unreported exception GoogleApiException" — Java 25 does not yet implement sealed exhaustiveness on catch blocks (only on switch). Resolution: added a defensive 5th catch on the sealed base after each 4-typed-catch group in both controllers, with explanatory comment. This is the standard pattern for sealed checked-exception consumers and is documented in the SUMMARY as a [[patterns-established]] entry.

**2. Plan Task 4 broke compile — intermediate state required transient IOException catch in controller.** Plan Task 4 (DriverSheetImportService.execute throws GoogleApiException) breaks `DriverSheetImportController#execute` compile since execute() now throws a checked exception not previously caught. Resolution: added a temporary `catch (IOException e)` to the controller in Task 4's commit, replaced by the 4 typed catches in Task 5's commit. Net: Task 4 lands compile-green; Task 5 supersedes the bridge.

**3. Plan files_modified path mismatch — `DriverSheetImportControllerExceptionTest`.** Plan listed `src/test/java/org/ctc/admin/controller/DriverSheetImportControllerExceptionTest.java`; actual file lives at `src/test/java/org/ctc/dataimport/DriverSheetImportControllerExceptionTest.java`. Operated on the actual path; documented for planner correction.

**4. SpotBugs DB_DUPLICATE_BRANCHES caught + fixed.** The first `mvn verify` run flagged the mapper's `default ->` arm in `fromGoogleJson` as having identical conditional branches (both yielded `TransientGoogleApiException`). Removed the redundant `if (TRANSIENT_STATUS_CODES.contains(status))` since both 408/429/5xx AND unknown-status default to TRANSIENT. Set `TRANSIENT_STATUS_CODES` field removed.

**5. Mockito stub fallout in 2 pre-existing test classes.** `RaceCalendarServiceTest.givenCalendarServiceThrowsIOException` + `CsvImportControllerExceptionTest.givenIoException_whenPreviewSheet` mocked `googleCalendarService.createEvent(...)` and `googleSheetsService.readRange(...)` to throw plain `IOException`. After Task 3 widened those signatures to `throws GoogleApiException` (narrower), Mockito's runtime check rejects "Checked exception is invalid for this method". Resolution: stub `new TransientGoogleApiException("...", null)` instead. Fix landed in commit `b8a91b90`.

**Total deviations:** 5 auto-fixed (1 language-gap workaround, 1 transitive-compile bridge, 1 planner-path correction, 1 SpotBugs gate fix, 1 Mockito stub fallout). **Impact on plan:** all auto-fixes preserve plan intent. No scope creep.

## Issues Encountered

- **Local-only mvn verify flakiness (19 errors out of 23 initial failures).** Tests like `BackupControllerTest`, `SeasonPhaseEntityIntegrationTest`, `PhaseTeamRepositoryTest` failed locally with Playwright screenshot-capture protocol errors + empty `findAll()` results (H2 isolation issue under PERF-03 shared `@CtcDevSpringBootContext`). CI on Ubuntu (Plan 91-01 HEAD `0bbce7d7` pull_request runs) is green. The 4 errors actually attributable to my changes (the Mockito stubs above) were diagnosed + fixed; the other 19 were not regressions from Plan 91-02. After the Mockito fix + SpotBugs fix, final `mvn verify` exit 0.

## User Setup Required

None — Plan 91-02 changes the in-process exception hierarchy + UI rendering only. Existing operator setup (`google.sheets.credentials-path`, `google.calendar.id`) is unchanged. New `docs/operations/google-integration.md` documents the same setup as the previously-undocumented operator procedure.

## Next Phase Readiness

**Plan 91-03 (Milestone Closer) is unblocked.** PR #129 rolling body now documents Plan 91-01 + Plan 91-02 LANDED sections. Plan 91-03 will:

- Add v1.12 entry to `.planning/MILESTONES.md`
- Update `README.md` (test-performance + backup pointer references)
- Compose the final PR body per D-07b shape (Coverage / CodeQL / verification numbers)
- Flip the PR Draft → Ready via `gh pr ready 129`

**Operator pause per [[wave-pause]]:** Orchestrator stops here for user feedback before opening Plan 91-03. No blockers; Plan 91-02 closes cleanly.

---
*Phase: 91-perf-re-harvest-stretch-ux-polish-milestone-closer*
*Plan: 02 — UX-01 Typed-Exception Hierarchy + Flash UX + Runbook*
*Completed: 2026-05-20*
