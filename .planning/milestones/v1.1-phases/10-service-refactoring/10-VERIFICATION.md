---
phase: 10-service-refactoring
verified: 2026-04-06T12:00:00Z
status: human_needed
score: 3/4 must-haves verified
human_verification:
  - test: "Navigate to /admin/tools/template-editors and test save/reset for each of the 10 graphic types (team-cards, lineup, settings, race-results, match-results, matchday-overview, matchday-schedule, matchday-results, overlay, power-rankings)"
    expected: "Save stores the template and shows success flash. Reset restores the default and shows success flash. The active tab is preserved via ?tab= redirect."
    why_human: "Requires running server and Playwright rendering of actual graphic templates to verify templates load and save correctly end-to-end"
  - test: "Navigate to a playoff with at least one bracket round and verify the bracket view displays correctly"
    expected: "Playoff bracket renders team names, seed numbers, aggregate points per leg, and winner indicators identically to pre-refactor behavior"
    why_human: "Visual layout verification; data flows through PlayoffBracketViewService -> ScoringService chain which cannot be traced statically for correctness"
  - test: "Create or edit a race and verify the form pre-populates correctly; submit with calendar event creation"
    expected: "Race form shows all matchdays, teams, cars, and tracks. Result form pre-fills driver grid. Calendar event is created/updated via Google Calendar API."
    why_human: "External calendar API interaction and end-to-end form wiring cannot be verified without running server and live Google Calendar credentials"
---

# Phase 10: Service Refactoring Verification Report

**Phase Goal:** Large service classes and duplicated controller code are split into focused, maintainable units
**Verified:** 2026-04-06T12:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | TemplateEditorController uses a generic Map<String, AbstractGraphicService> dispatch instead of 30+ copy-paste blocks | VERIFIED | Controller has exactly 2 POST handlers (`/{templateType}/save`, `/{templateType}/reset`), 3 static lookup maps (TEMPLATE_TYPE_TO_BEAN, TEMPLATE_TYPE_TO_ATTR, TEMPLATE_TYPE_TO_LABEL), and `Map<String, TemplateManageable> templateServices` with Spring Map autowiring |
| 2 | PlayoffService bracket-view and seeding logic are in separate focused services | VERIFIED | PlayoffBracketViewService.java (161 lines, getBracketView + view classes) and PlayoffSeedingService.java (177 lines, 5 seeding methods) created; PlayoffService reduced from 621 to 339 lines; seeding methods absent from PlayoffService |
| 3 | RaceService form-data assembly and calendar-event logic are in separate focused services | VERIFIED | RaceFormDataService.java (181 lines, @Transactional(readOnly=true), 3 form data methods + helpers) and RaceCalendarService.java (76 lines, createOrUpdateCalendarEvent + isCalendarAvailable) created; RaceService reduced from 525 to 347 lines; no GoogleCalendarService field in RaceService |
| 4 | All graphic editing, playoff, and race functionality works identically from the UI | HUMAN NEEDED | Code wiring is correct (controllers delegate to new services, no stubs found), but visual end-to-end behavior requires running server verification |

**Score:** 3/4 truths verified (4th requires human testing)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/org/ctc/admin/service/TemplateManageable.java` | Interface contract for template-managing services | VERIFIED | Exists, 10 lines, declares 4 methods: loadTemplate(), saveTemplate(String), resetTemplate(), hasCustomTemplate() |
| `src/main/java/org/ctc/admin/controller/TemplateEditorController.java` | Generic dispatch controller with 2 endpoints instead of 20 | VERIFIED | 148 lines (down from ~380), contains only 2 POST handlers, 3 static maps, Map<String, TemplateManageable> field; old methods (saveTeamCardTemplate, resetLineupTemplate) absent |
| `src/main/java/org/ctc/domain/service/PlayoffBracketViewService.java` | Bracket view assembly service | VERIFIED | 161 lines, getBracketView(UUID), buildMatchupView private helper, 4 inner view classes (PlayoffBracketView, RoundView, MatchupView, LegView), no PlayoffService injection, no private calculateTeamTotals |
| `src/main/java/org/ctc/domain/service/PlayoffSeedingService.java` | Seeding logic service | VERIFIED | 177 lines, contains seedTeam, autoSeedBracket, saveSeed, saveSeedNumbers, getSeedingData, SeedingData record |
| `src/main/java/org/ctc/domain/service/RaceFormDataService.java` | Read-only form data assembly service | VERIFIED | 181 lines, @Transactional(readOnly=true) at class level, getNewRaceFormData, getRaceFormData, getResultsFormData all returning RaceService.* record types |
| `src/main/java/org/ctc/domain/service/RaceCalendarService.java` | Calendar event management service | VERIFIED | 76 lines, GoogleCalendarService injected, createOrUpdateCalendarEvent + isCalendarAvailable() delegation |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| TemplateEditorController | Map<String, TemplateManageable> | Spring Map autowiring + TEMPLATE_TYPE_TO_BEAN lookup | WIRED | Line 61: `private final Map<String, TemplateManageable> templateServices`; line 90-96: `TEMPLATE_TYPE_TO_BEAN.get(templateType)` null-checked before dispatch |
| PlayoffController | PlayoffBracketViewService | constructor injection | WIRED | Line 33: `private final PlayoffBracketViewService playoffBracketViewService`; import confirmed |
| PlayoffController | PlayoffSeedingService | constructor injection | WIRED | Line 34: `private final PlayoffSeedingService playoffSeedingService`; getSeedingData, autoSeedBracket, saveSeed all call playoffSeedingService |
| PlayoffBracketViewService | ScoringService | constructor injection | WIRED | Line 31: `private final ScoringService scoringService`; line 75: `scoringService.calculateTeamTotals(...)` called in buildMatchupView |
| RaceController | RaceFormDataService | constructor injection | WIRED | Lines 75, 88, 101, 261 call raceFormDataService.getNewRaceFormData/getRaceFormData/getResultsFormData/getUsedSelections |
| RaceController | RaceCalendarService | constructor injection | WIRED | Line 191: `raceCalendarService.createOrUpdateCalendarEvent(id)` |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| PlayoffBracketViewService | PlayoffBracketView | playoffRepository.findById + raceRepository.findByPlayoffMatchupRoundPlayoffId + playoffSeedRepository.findByPlayoffId | Yes — DB queries, no hardcoded returns | FLOWING |
| RaceFormDataService | RaceFormData | raceRepository, matchdayRepository, teamRepository, carRepository, trackRepository, raceLineupRepository, seasonDriverRepository | Yes — real repository queries | FLOWING |
| RaceCalendarService | (side effect: calendar event) | raceRepository.findById + googleCalendarService.createEvent/updateEvent | Yes — real external API call | FLOWING |

### Behavioral Spot-Checks

Step 7b: SKIPPED — services require a running Spring context with H2 database and Google Calendar credentials; no standalone runnable entry points for individual service methods.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| ARCH-03 | Plan 01 | TemplateEditorController nutzt generischen Ansatz mit Map<String, AbstractGraphicService> statt 30+ Copy-Paste-Bloecke | SATISFIED | Controller uses Map<String, TemplateManageable> dispatch; 20 old methods replaced by 2 generic endpoints; 9 service classes implement TemplateManageable covering all 10 template types |
| ARCH-04 | Plan 02 | PlayoffService in fokussierte Services aufgeteilt (Bracket-View, Seeding separiert) | SATISFIED | PlayoffBracketViewService and PlayoffSeedingService extracted; PlayoffService reduced from 621 to 339 lines; calculateTeamTotals moved to ScoringService (D-06 no duplication); SiteGeneratorService auto-fixed to use PlayoffBracketViewService |
| ARCH-05 | Plan 03 | RaceService in fokussierte Services aufgeteilt (FormData-Assembly, Calendar-Events separiert) | SATISFIED | RaceFormDataService and RaceCalendarService extracted; RaceService reduced from 525 to 347 lines; GoogleCalendarService removed from RaceService; RaceController updated with 3-service injection |

Note: REQUIREMENTS.md still shows ARCH-03, ARCH-04, ARCH-05 as "Pending" (marked with `[ ]`) — the traceability table has not been updated to mark them complete. This is a documentation gap only; the implementation is complete.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| None found | — | — | — | — |

All new files contain real implementations with repository queries and service delegations. No TODO/FIXME comments, no placeholder returns, no hardcoded empty data structures in rendering paths.

### Human Verification Required

#### 1. Template Editor End-to-End (All 10 Template Types)

**Test:** Start dev server (`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`). Navigate to `/admin/tools/template-editors`. For each tab (team-cards, lineup, settings, race-results, match-results, matchday-overview, matchday-schedule, matchday-results, overlay, power-rankings): (a) verify template content loads in editor, (b) modify and save, (c) check success flash and active tab preserved, (d) reset to default, (e) check reset flash.
**Expected:** All 10 template types respond correctly. Invalid path (manual POST to `/admin/tools/template-editors/bogus/save`) returns "Unknown template type" error flash. No 500 errors.
**Why human:** Playwright rendering of graphic templates requires a running server; Spring Map<String, TemplateManageable> autowiring cannot be verified without live Spring context.

#### 2. Playoff Bracket View After Refactoring

**Test:** Navigate to a playoff with at least one round and multiple matchups. Verify bracket display: team names, seed numbers, per-leg scores, aggregate points, winner indicators.
**Expected:** Bracket renders identically to pre-refactor behavior. PlayoffBracketViewService.getBracketView → ScoringService.calculateTeamTotals chain produces correct aggregate scores.
**Why human:** Visual layout verification; correctness of multi-leg point aggregation across the new service boundary requires visual confirmation with known test data.

#### 3. Race Form and Calendar Integration

**Test:** Navigate to create a new race (from a matchday). Verify form shows populated matchdays, teams, cars, tracks. Edit an existing race with results — verify driver grid pre-fills. If Google Calendar credentials available: save a race with a date/time set and verify calendar event creation.
**Expected:** Form data assembly works identically to pre-refactor. RaceFormDataService methods return correctly populated records. RaceCalendarService delegates to GoogleCalendarService.
**Why human:** External Google Calendar API interaction cannot be verified without live credentials; form pre-population correctness requires running server with real data.

### Gaps Summary

No blocking gaps found. All three automated truths (SC1, SC2, SC3 from the roadmap) are fully implemented and wired. The fourth success criterion (SC4: UI works identically) requires human verification per the VALIDATION.md contract established before execution.

One documentation note: REQUIREMENTS.md traceability table still shows ARCH-03/ARCH-04/ARCH-05 as "Pending". These should be marked complete after human verification confirms SC4.

---

_Verified: 2026-04-06T12:00:00Z_
_Verifier: Claude (gsd-verifier)_
