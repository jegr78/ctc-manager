---
plan_id: 61-gap-07
phase: 61-cleanup-quality-gate
status: complete
completed: 2026-05-02T00:05:00Z
gap_closure: true
---

# 61-gap-07 — Dead code audit: admin + dataimport + sitegen + gt7sync

## Outcome

Audit-only pass — no additional dead code found beyond what `61-gap-06` already removed.

## Audit method

For each file in scope:
1. Listed all `private` methods and counted in-file occurrences (declaration + calls).
2. Checked all injected `private final` fields against in-file usage counts.
3. Cross-referenced any candidate against `src/test` to catch test-only usage paths.
4. Inspected the heavy files (`TestDataService`, `DriverSheetImportService`,
   `SiteGeneratorService`) by hand for orphan helpers from earlier cascade-migration
   churn (e.g., `legacyStandings*`, `seasonsWithoutRegularPhase*` — none found).

## Findings

- **TestDataService**: every `seedX` helper is called from `seed()` or its sub-helpers.
  No dead methods. The Phase 61 cleanup confirmed the file structure stays as the
  central dev/demo seeder.
- **DriverSheetImportService**: only two private helpers (`buildTabPreview`,
  `cellToString`); both are actively called multiple times. Phase-59 D-XX helpers from
  the original plan no longer exist as separate methods (they were inlined during
  Phase 59-61 simplifications).
- **CsvImportService**: private helpers (`parseIntSafe`, `parseBooleanSafe`,
  `findOrCreateMatchday`, `findTeamFlexible`, `groupByTeamPair`, `readCsvLines`) are
  all called from the `import()` entry point or its delegates.
- **SiteGeneratorService**: WR-01 fix (alltime-standings fallback removal) was clean —
  no orphan `legacyStandings*` helpers remain. The single `fallback` match in the file
  is a log message, not a dead helper.
- **gt7sync**: small files with linear fetch→parse→save flow; all private helpers
  actively called.

## Defensive null-guard classification

All defensive guards in this layer fall into KEEP categories per CLAUDE.md "validate
at system boundaries":

| Pattern | Disposition | Boundary |
|---------|-------------|----------|
| Controller `@RequestParam`/`@PathVariable` null-checks | KEEP | HTTP layer (system boundary) |
| `IOException` catches in `dataimport` | KEEP | Filesystem + Google Sheets API |
| `DataIntegrityViolationException` catches | KEEP | Spring framework boundary |
| `NumberFormatException` in CSV-row parsing | KEEP | User-supplied data |
| Jsoup `.first()` returns + null-checks in `gt7sync` | KEEP | External HTTP scraping |

## Commits

- `4d4ef7b refactor(61-gap-07): audit complete — no additional dead code beyond gap-06 findings`
  (empty commit — preserves the audit record in git history)

## Acceptance criteria

- [x] Admin/dataimport/sitegen/gt7sync layers audited for unused private methods + fields
- [x] All defensive null-guards in scope classified as KEEP with boundary rationale
- [x] DevDataSeeder integration path unchanged (no methods touched)
- [x] No removal performed without zero-callers proof — none qualified

## Self-Check: PASSED

The cascade-migration churn that earlier waves cleaned up (gap-01..gap-05) appears to
have already removed any orphan helpers from this layer. The two unused fields handled
in `gap-06` were the only confirmed dead-code findings across the full Phase 61
sweep.
