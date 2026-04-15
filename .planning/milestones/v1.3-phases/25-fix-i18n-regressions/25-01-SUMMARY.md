---
phase: 25-fix-i18n-regressions
plan: 01
status: complete
started: 2026-04-10
completed: 2026-04-10
requirements_satisfied: [I18N-03, I18N-04]
---

# Plan 25-01: Fix I18N Regressions — Summary

## Objective
Remove German text regressions introduced by Phase 23 in test files and HTML templates.

## What Was Built

### Task 1: Replace "Spieltag" with "Matchday" in StandingsServiceTest.java (I18N-03)
- Replaced all 24 occurrences of `"Spieltag"` with `"Matchday"` in Matchday constructor calls
- Commit: `fix(i18n): replace 24 German "Spieltag" with "Matchday" in StandingsServiceTest`

### Task 2: Translate German HTML comments to English (I18N-04)
- `team-detail.html:82`: `<!-- Seasons ohne Fahrer -->` → `<!-- Seasons without drivers -->`
- `matchday-detail.html:69`: `<!-- Legs (nur anzeigen bei Multi-Leg oder wenn Legs vorhanden) -->` → `<!-- Legs (only show for multi-leg or when legs exist) -->`
- Commit: `fix(i18n): translate German HTML comments to English in templates`

## Key Files

### Modified
- `src/test/java/org/ctc/domain/service/StandingsServiceTest.java` — 24 string replacements
- `src/main/resources/templates/admin/team-detail.html` — 1 comment translation
- `src/main/resources/templates/admin/matchday-detail.html` — 1 comment translation

## Verification
- Zero "Spieltag" occurrences in StandingsServiceTest.java ✓
- Zero German HTML comments in templates ✓
- 828 tests passing, 0 failures ✓
- JaCoCo coverage check passed ✓

## Issues Encountered
None.

## Self-Check: PASSED
