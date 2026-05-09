---
phase: 59-import-test-data
plan: 1
subsystem: domain-service
tags: [java, spring-boot, service-wrapper, business-rule-exception, season-disambiguation, tdd]
completed: 2026-04-29

dependency_graph:
  requires: []
  provides:
    - SeasonManagementService.findUnique(int year, int number)
    - SeasonManagementService.findUnique(int year)
  affects:
    - Plan 59-02: DriverSheetImportService tab-resolution (calls both overloads)
    - Plan 59-04: Integration tests (asserts BusinessRuleException surfaces as ambiguousReason)

tech_stack:
  added: []
  patterns:
    - "@Transactional(readOnly = true) service wrapper over List<> repository result"
    - "BusinessRuleException on multi-hit (0/1/many contract)"

key_files:
  modified:
    - src/main/java/org/ctc/domain/service/SeasonManagementService.java
    - src/test/java/org/ctc/domain/service/SeasonManagementServiceTest.java

decisions:
  - "findUnique method name chosen (over findOneByYearAndNumber) for concision and idiomatic Optional semantics"
  - "Overload findUnique(int year) added as service-side helper so importer has a single API to call (D-02 option 2)"
  - "Exception messages verbatim-locked: 'Multiple seasons exist for (year, number) — consolidate them first or rename sheet tab to disambiguate' and 'Multiple seasons exist for year {year} — consolidate them first or rename sheet tab to disambiguate'"

requirements-completed: [IMPORT-02]

metrics:
  duration_minutes: ~8
  tasks_completed: 2
  tests_added: 6
  files_created: 0
  files_modified: 2
---

# Phase 59 Plan 1: SeasonManagementService findUnique Wrapper Summary

Service-layer wrapper around `SeasonRepository.findByYearAndNumber` and `findByYear` that enforces the "exactly one season" contract via `BusinessRuleException` on multi-hit.

## What Was Built

### Production Methods Added

**`SeasonManagementService.java`** — two new `@Transactional(readOnly = true)` methods added after `findActiveSeason` (lines ~95–133 in the modified file):

```java
public Optional<Season> findUnique(int year, int number)
```
Wraps `SeasonRepository.findByYearAndNumber(int, int)` (returns `List<Season>`). Enforces:
- 0 hits → `Optional.empty()`
- 1 hit  → `Optional.of(season)`
- >1 hits → `BusinessRuleException("Multiple seasons exist for (" + year + ", " + number + ") — consolidate them first or rename sheet tab to disambiguate")`

```java
public Optional<Season> findUnique(int year)
```
Wraps `SeasonRepository.findByYear(int)`. Same 0/1/many contract. Exception message:
`"Multiple seasons exist for year " + year + " — consolidate them first or rename sheet tab to disambiguate"`

### Exact Exception Messages (locked per D-02 / D-18)

These exact strings are asserted by Plan 59-02 unit tests and Plan 59-04 IT:

| Overload | Exception Message |
|----------|------------------|
| `findUnique(int year, int number)` | `Multiple seasons exist for (2023, 1) — consolidate them first or rename sheet tab to disambiguate` |
| `findUnique(int year)` | `Multiple seasons exist for year 2023 — consolidate them first or rename sheet tab to disambiguate` |

### Tests Added (6 new, all GREEN)

| Test Method | Covers |
|-------------|--------|
| `givenNoSeason_whenFindUniqueByYearAndNumber_thenReturnsEmpty` | two-arg, 0 hits |
| `givenExactlyOneSeason_whenFindUniqueByYearAndNumber_thenReturnsOptionalOf` | two-arg, 1 hit |
| `givenMultipleSeasons_whenFindUniqueByYearAndNumber_thenThrowsBusinessRule` | two-arg, >1 hits |
| `givenNoSeason_whenFindUniqueByYear_thenReturnsEmpty` | one-arg, 0 hits |
| `givenExactlyOneSeasonForYear_whenFindUniqueByYear_thenReturnsOptionalOf` | one-arg, 1 hit |
| `givenMultipleSeasonsForYear_whenFindUniqueByYear_thenThrowsBusinessRule` | one-arg, >1 hits |

## TDD Compliance

- RED commit: `0c3d1b0` — failing tests (compile error: `findUnique` symbol not found)
- GREEN commit: `b8ebce9` — implementation added; all 6 tests pass

## Regression Check

Full test suite after implementation: **1133 tests, 0 failures** (Phase 58 baseline was 1127; +6 from this plan).

## Invariants Confirmed

- `SeasonRepository.java` untouched (D-19 invariant: `findByYearAndNumber` stays `List<Season>`)
- No new Flyway migration created (D-17 invariant: no DB UNIQUE constraint)
- `@Transactional(readOnly = true)` count: 13 (was 11 before; +2 for the two new overloads)

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None. Both `findUnique` overloads delegate to real repository calls; no placeholder data.

## Threat Flags

None. This plan adds an internal service method only (no new endpoint, no new DTO binding, no external API call). All threats accepted per plan threat model (T-59-01-01 through T-59-01-03).

## Self-Check

### Created Files

- No new files created.

### Modified Files

- `src/main/java/org/ctc/domain/service/SeasonManagementService.java` — FOUND (verified by compile + test run)
- `src/test/java/org/ctc/domain/service/SeasonManagementServiceTest.java` — FOUND (verified by 6 new test methods present)

### Commits

- `0c3d1b0` — RED test commit
- `b8ebce9` — GREEN implementation commit

## Self-Check: PASSED
