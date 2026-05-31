---
phase: 111
plan: 02
type: execute
status: complete
requirements: [SEC-LOG-02]
---

# Plan 111-02 Summary: Wrap all flagged log call sites

## What was built

Routed every CodeQL-flagged user-controlled log argument (+ same-statement
siblings per D-06) through `LogSanitizer.sanitize()` at the log call site across
17 files, in two atomic commits. Source fix only — no `query-filters`, no
`@SuppressFBWarnings`, no entry-point mutation (D-04/D-05/D-06/D-07).

## Key files (17 + 1 clone)

### Task 1 — dataimport / admin.controller / backup (alerts 1–18, 9 files)
- `admin/controller/DriverController` (1), `DriverSheetImportController` (1),
  `TemplateEditorController` (2)
- `backup/exception/BackupUploadExceptionHandler` (1), `backup/lock/ImportLockedWriteRejector` (2 — D-06 sibling), `backup/service/BackupImportService` (1)
- `dataimport/CsvImportService` (alerts 8–11), `DriverMatchingService` (alerts 12–16, searchTerm + getPsnId siblings), `DriverSheetImportService` (alerts 17–18)
- Each file gained one `import static org.ctc.util.LogSanitizer.sanitize;`.

### Task 2 — domain.service (alerts 19–29, 8 files)
- `FileStorageService` (19), `MatchdayService` (20 + 21 special case),
  `PlayoffService` (22), `ScoringService` (23 — whole ternary),
  `SeasonManagementService` (24, 25), `SeasonPhaseService` (26, 27),
  `StandingsViewService` (28), `TeamManagementService` (29 — D-06 sibling).

## Decisions implemented
- **D-04** — sanitize at each log call site; wrap only the user-controlled `{}` arg.
- **D-05** — no entry-point mutation: `matchday.setScheduledWeekend(scheduledWeekend)`, `searchTerm`, `row.psnId()`, repository inputs left raw.
- **D-06** — flagged args + same-statement siblings wrapped; numeric/long/UUID/enum args left unwrapped; no blanket sweep.
- **D-07** — no suppressions added.
- **MatchdayService special case** — the ad-hoc `safeWeekend = …replaceAll("[\\r\\n]","_")` (NOT a CodeQL-recognised barrier) was deleted; the value now flows through `sanitize(scheduledWeekend)`. `grep safeWeekend` → 0.

## Deviation (user-approved)
- **CsvImportService line ~311 clone wrapped** — the "Overwriting existing match"
  log appears twice (line 194 flagged as alert 8; line ~311 an identical clone
  inside an `existing.ifPresent(m -> …)` lambda, **not** flagged — likely a CodeQL
  lambda-flow gap). User chose (interactive checkpoint) to wrap the clone too, to
  close the latent CWE-117 and keep "0 alerts" durable. Minimal scope step beyond
  the 29; both occurrences wrapped via `replace_all`.

## Note on plan acceptance-grep expressions
Two ACs used grep patterns that under-report on correct code (recorded for future
plan-quality): `grep -c 'sanitize('` counts *lines* not occurrences (CsvImportService
has 13 occurrences across 5 lines), and `sanitize\([^)]*getPsnId` cannot match
`sanitize(exact.get().getPsnId())` because `get()` contains a `)`. Verified the
real code with `grep -o`: 5× `sanitize(searchTerm)`, 4× `getPsnId` sibling wraps.

## Verification
- `./mvnw test-compile` → exit 0 (all 17 files compile with the new import).
- Targeted Surefire run (`MatchdayServiceTest,ScoringServiceTest,PlayoffServiceTest,SeasonPhaseServiceTest,SeasonManagementServiceTest,TeamManagementServiceTest,StandingsViewServiceTest,FileStorageServiceTest`) → **Tests run: 203, Failures: 0, Errors: 0** (SeasonManagementServiceTest contributes its ITs elsewhere; 0 plain unit tests there).
- `safeWeekend`=0, `sanitize(scheduledWeekend)`=1, old `[\\r\\n]` replaceAll=0, `setScheduledWeekend` intact.

## Commits
- `e0a3685f` fix(111-02): sanitize … dataimport/controller/backup (alerts 1–18 + clone)
- `1a55e9f9` fix(111-02): sanitize … domain/service (alerts 19–29)

## Self-Check: PASSED

## Next
Plan 111-03 (autonomous: false, checkpoint) — full clean build gate
(`./mvnw clean verify -Pe2e`), push, CodeQL re-scan on PR #132 must report 0 open
`java/log-injection` alerts; conditionally add a `models-as-data` barrier model
pack only if CodeQL does not auto-recognise the `sanitize()` helper boundary.
