---
status: issues_found
phase: 22-dev-teams-drivers
depth: standard
files_reviewed: 2
findings:
  critical: 1
  warning: 2
  info: 0
  total: 3
---

# Phase 22 Code Review

## Files Reviewed

- `src/main/java/org/ctc/admin/TestDataService.java`
- `src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java`

## Summary

The real-to-fictive data replacement is complete and correct. TeamCardService injection follows Lombok `@RequiredArgsConstructor` + `final` pattern correctly. Error handling for Playwright unavailability is graceful. E2E test data isolation (T-ALF, T-BRV) is fully preserved. Three issues found.

---

## Critical Issues

### CRITICAL-1: Demo logo classpath resources not updated — all logos silently absent in dev profile

**Confidence: 100**
**File:** `src/main/java/org/ctc/admin/TestDataService.java`, line 247

`copyDemoLogos()` builds a classpath path from team short name. `src/main/resources/demo/team-logos/` contains only old real-team filenames (AHR.png, ART.png, CLR.png, etc.). None of the new fictive short names (VRX, SGM, ADR, TBR, ICL, SVT, NFR, EGP, HMS, PWR) have matching logo files. Every `resource.exists()` returns false — zero logos copied — yet the method logs success.

**Fix:** Replace or rename logo PNGs to match new short names. Fix the log message to report actual copied count.

---

## Warning Issues

### WARNING-1: race2 missing RaceSettings — inconsistent test fixture

**Confidence: 85**
**File:** `src/main/java/org/ctc/admin/TestDataService.java`, lines 553-556

`race1` receives `createTestSettings(race1)` before save. `race2` is saved without settings. Inconsistent fixture; any code accessing `race.getSettings()` on race2 without null guard will NPE.

**Fix:** Add `race2.setSettings(createTestSettings(race2));` before save.

### WARNING-2: German comments violate CLAUDE.md English-only code comment policy

**Confidence: 100**
**File:** `src/main/java/org/ctc/admin/TestDataService.java`, lines 510, 512, 518

Three German comments remain. CLAUDE.md mandates English comments.

**Fix:** Translate to English.

---

## Passed Checks

- Data replacement complete: all fictive, no real CTC identifiers
- TeamCardService injection correct via Lombok
- Graceful Playwright error handling
- E2E test data isolation preserved (T-ALF, T-BRV untouched)
- Integration test structure follows Given-When-Then convention
- 11 tests covering team counts, sub-team structure, colors, and isolation
