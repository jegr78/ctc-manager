# Phase 8: Exception Refinement - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-05
**Phase:** 08-exception-refinement
**Areas discussed:** Exception type mapping, Graphic service exceptions, Unbounded query scoping, Scope boundary
**Mode:** --auto (all decisions auto-selected with recommended defaults)

---

## Exception Type Mapping

| Option | Description | Selected |
|--------|-------------|----------|
| Map by actual throw sources | Analyze each catch site, determine actual exceptions thrown by called code | ✓ |
| Use broad categories only | Replace Exception with RuntimeException everywhere | |
| Keep catch(Exception e) with TODO | Mark for later refinement | |

**User's choice:** [auto] Map by actual throw sources (recommended default)
**Notes:** Controllers calling services → catch specific service exceptions. IO operations → IOException. Unexpected → propagate to GlobalExceptionHandler.

---

## Graphic Service Exceptions

| Option | Description | Selected |
|--------|-------------|----------|
| Catch RuntimeException only | Playwright throws RuntimeException; covers all graphic failures | ✓ |
| Catch specific Playwright exceptions | Import Playwright exception types directly | |
| Keep Exception for safety | Graphic endpoints can't afford to show error pages | |

**User's choice:** [auto] Catch RuntimeException only (recommended default)
**Notes:** AbstractGraphicService wraps Playwright failures as RuntimeException. Narrowing to RuntimeException is sufficient — checked exceptions would indicate a programming error and should propagate.

---

## Unbounded Query Scoping

| Option | Description | Selected |
|--------|-------------|----------|
| Require seasonId where possible | Remove unscoped fallback paths, UI always provides seasonId | ✓ |
| Add Pageable everywhere | Full pagination support | |
| Add result limits | Cap at N results without pagination UI | |

**User's choice:** [auto] Require seasonId where possible (recommended default)
**Notes:** Focus on QUAL-02 targets: RaceService, DriverRankingService, DriverService. Admin list views with small datasets (teams, scorings) remain as-is.

---

## Scope Boundary

| Option | Description | Selected |
|--------|-------------|----------|
| Defer TemplateEditorController to Phase 10 | ARCH-03 refactors entire controller, fixing catches now is throwaway | ✓ |
| Include TemplateEditorController | Fix catches as part of this phase | |

**User's choice:** [auto] Defer to Phase 10 (recommended default)
**Notes:** Phase 10 (ARCH-03) completely refactors TemplateEditorController with generic Map<String, AbstractGraphicService> approach. DemoDataSeeder also excluded (not production, excluded from coverage).

---

## Claude's Discretion

- Multi-catch syntax vs separate catch blocks
- Exact Gt7 batch operation exception types
- DriverRankingService repository query optimization vs in-memory filter
- Test coverage strategy for catch-block refinements

## Deferred Ideas

- TemplateEditorController catch blocks → Phase 10 (ARCH-03)
- DemoDataSeeder catch block → excluded from coverage
