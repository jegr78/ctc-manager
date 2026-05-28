---
phase: 92
plan: 02
slug: carry-forwards-cleanup
status: shipped
shipped: 2026-05-21
requirement: COV-01
---

# Plan 92-02 — COV-01 JaCoCo Recovery via RaceControllerCalendarTest + 2 ITs

Restored the v1.11 JaCoCo line-coverage baseline (≥ 88.88 %) by adding focused
test coverage for the `RaceController` calendar branches and the `GoogleApiExceptionMapper`
translation paths that drove the v1.12 Δ −0.44 pp regression. Test-only plan;
`src/main/**` git-clean per CONTEXT D-10.

## Files modified

| File | Change |
|------|--------|
| `src/test/java/org/ctc/admin/controller/RaceControllerCalendarTest.java` | NEW — 9 `@Test` methods covering 4 typed-catch arms (AUTH/NOT_FOUND/PERMISSION/TRANSIENT) + `IllegalStateException` + happy path + 3 GET model attributes (`calendarAvailable`/`hasCalendarEvent`/`canCreateCalendarEvent`). GET tests use a `MockMvcBuilders.standaloneSetup` MockMvc with a noop view-resolver to exercise the GET handler model population without forcing the full `race-detail.html` template to render (which would require hydrating Race+Matchday+Match+Track+Season — unrelated to COV-01). |
| `src/test/java/org/ctc/dataimport/GoogleSheetsServiceIT.java` | NEW IT sibling — 3 `@Test` methods exercising real Spring-wired `GoogleSheetsService` + real `GoogleApiExceptionMapper` translation: IOException → Transient (default), 404 GoogleJsonResponseException → NotFound, IOException on `readRange` → Transient. `@SpringBootTest + @ActiveProfiles("dev") + @Transactional + @Tag("integration")`. Reflective injection of mocked `sheetsClient` field. |
| `src/test/java/org/ctc/dataimport/GoogleCalendarServiceIT.java` | NEW IT sibling — 3 `@Test` methods exercising 403/reason="authError" → Auth (discriminator subcase), 403/reason="forbidden" → Permission (fall-through), and `GoogleApiExceptionMapper.from(GeneralSecurityException)` → Auth (static helper unit). |
| `src/test/java/org/ctc/dataimport/CsvImportControllerExceptionTest.java` | Extended with 4 additional happy-path tests (preview-sheet success render, empty-raceSheets early-return, scorecard-parser-empty branch, no-file-or-sheet validation in execute) — closes the JaCoCo gap from 88.48 → 88.88. |
| `.planning/phases/92-carry-forwards-cleanup/92-02-VALIDATION.md` | NEW — per-plan Nyquist slice per CONTEXT D-08. |

## ./mvnw verify summary

- BUILD SUCCESS — total time 7:48 min
- Tests run: **1452** (Failures: 0, Errors: 0, Skipped: 1) — Δ +14 vs Plan 92-01 ship state
- JaCoCo line coverage: **88.8838 %** (covered=7700, missed=963) — **v1.11 baseline restored** (≥ 88.88 %); Δ +0.44 pp over v1.12 (88.44 %)
- SpotBugs `BugInstance` count: **0** (per CONTEXT D-07)
- W4 header-guard: columns 8/9 confirmed `LINE_MISSED`/`LINE_COVERED`
- W5 disposition: organic recompute reached the target — no JaCoCo `<exclude>` rule needed
- `git diff --stat src/main/`: empty (test-only plan per CONTEXT D-10 — strictest D-10 enforcement)

## Coverage delta

| Stage | JaCoCo | Δ |
|-------|--------|---|
| v1.11 baseline | 88.88 % | — |
| v1.12 (carry-forward shortfall) | 88.44 % | −0.44 |
| Plan 92-01 ship (typed-catch refactor) | 88.23 % | −0.65 from v1.11 |
| Plan 92-02 after RaceControllerCalendarTest + 2 ITs | 88.48 % | −0.40 from v1.11 |
| Plan 92-02 after extra Csv happy-path tests | **88.79 %** | −0.09 from v1.11 |
| Plan 92-02 final ship (added 1 more parse-empty test) | **88.88 %** | **0.00 — RESTORED** |

## Defensive sealed-base catch arm note

The defensive `catch (GoogleApiException e)` arm on the sealed base of `CsvImportController`,
`DriverSheetImportController`, and `RaceController` is unreachable at runtime (sealed permits
forbid external subclassing). It is required by javac (sealed-exhaustiveness on catch blocks
is not yet a language feature). The arm remains technically uncovered in JaCoCo but the
organic gain from the 4 newly tested typed-catch arms + 3 GET model attribute tests + 3 mapper
ITs + 4 Csv happy-path tests more than compensated for the unreachable LoC.

## W9 pre-flight grep outcome

`grep -n "private final" src/main/java/org/ctc/admin/controller/RaceController.java` returned
5 collaborators matching the reflective list (no drift since context capture):
`RaceService`, `RaceFormDataService`, `RaceCalendarService`, `RaceAttachmentService`,
`RaceGraphicService`. All 5 declared as `@MockitoBean` in the new test.

## Rolling Draft milestone PR

- URL: https://github.com/jegr78/ctc-manager/pull/130
- State: Draft (preserved per CONTEXT D-06; Plan 98-03 flips Draft → Ready-for-review)
- Body update via `gh pr edit --body-file`: appends "Plan 92-02 shipped — JaCoCo recovery to 88.88 %" line

## Phase 92 VALIDATION.md row status flips

92-02-01..07: ⬜ → ✅ (all 7 rows green on plan ship).

## Per-plan 92-02-VALIDATION.md

Authored at `.planning/phases/92-carry-forwards-cleanup/92-02-VALIDATION.md` with
`nyquist_compliant: true` + 7-row Per-Task Verification Map + W4/W5 disposition +
W9 pre-flight outcome + Sign-Off block (per CONTEXT D-08).
