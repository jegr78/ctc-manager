---
plan_id: 61-gap-08
phase: 61-cleanup-quality-gate
status: complete
completed: 2026-05-02T00:15:00Z
gap_closure: true
---

# 61-gap-08 — Codebase-wide defensive over-validation + dead imports sweep

## Outcome

- 22 unused imports removed across 14 files (1 in `src/main`, 21 in `src/test`)
- Defensive over-validation patterns verified clean codebase-wide

## Defensive validation audit

| Pattern | Hits | Disposition |
|---------|------|-------------|
| `Objects.requireNonNull` / `Validate.notNull` / `Preconditions.checkNotNull` | 0 | Already absent |
| `assertNotNull(service)` / `assertNotNull(repository)` redundant on @Autowired | 0 | Already absent |
| `verifyNoMoreInteractions` (redundant) | 0 | Already absent |
| `catch (Throwable t)` in test code | 0 | Already absent |

The codebase already follows CLAUDE.md "trust internal code and framework guarantees".
Earlier waves (`gap-06`, `gap-07`) addressed the only borderline boundary cases.

## Unused imports removed

**src/main (1):**
- `domain/service/DriverService.java` — `org.ctc.domain.model.PsnAlias`

**src/test (21):**
- `db/migration/V4MigrateSeasonsToPhasesIT.java` — `java.util.List`
- `dataimport/DriverSheetImportControllerTest.java` — `Mockito.anyString`
- `dataimport/DriverSheetImportServiceIT.java` — `SeasonFormat`, `PhaseLayout`, `UUID`
- `dataimport/DriverSheetImportServiceTest.java` — `MatchType`, `TabWarning`, `PhaseType`,
  `PhaseLayout`, `SeasonPhaseGroup`
- `dataimport/DriverSheetImportServiceTransactionIT.java` — `SeasonFormat`
- `e2e/GroupsSeasonE2ETest.java` — `SeasonPhase`
- `admin/dto/SeasonPhaseFormTest.java` — `SeasonFormat`, `UUID`
- `admin/controller/SeasonControllerTest.java` — `SeasonFormat`
- `admin/controller/MatchdayControllerTest.java` — `MatchdayForm`
- `admin/controller/SeasonPhaseGroupControllerTest.java` — `SeasonFormat`
- `admin/controller/integration/SeasonPhaseControllerIT.java` — `PhaseLayout`, `SeasonFormat`
- `domain/service/SeasonPhaseServiceTest.java` — `Mockito.eq`
- `domain/service/RaceServiceTest.java` — `assertThatThrownBy`

## Method

For every `.java` file in `src/main` + `src/test`:
1. List every `import X.Y;` (non-wildcard, non-static-from-glob).
2. Extract the simple class name (last `.`-segment).
3. Check whether the class name appears anywhere in the file outside the `import`
   block (`grep -v "^import" file | grep -q "\b$cls\b"`).
4. If absent → strip the import line.

A Perl in-place rewrite stripped the matched lines deterministically.

## Commits

- `fef29ec refactor(61-gap-08): remove 22 unused imports across src/main + src/test`

## Test gate

`./mvnw test -Dtest='RaceServiceTest,SeasonPhaseServiceTest,SeasonControllerTest,MatchdayControllerTest'`

→ `Tests run: 71, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS

`./mvnw test-compile` → BUILD SUCCESS (verifies all 14 files still compile after the
import cleanup).

## Acceptance criteria

- [x] `Objects.requireNonNull` count: 0 codebase-wide (confirmed)
- [x] All over-validation patterns absent (`Validate.notNull`, `Preconditions.checkNotNull`,
      redundant `assertNotNull`, broad `catch (Throwable ...)`)
- [x] Codebase-wide unused-import scan returns 0 hits after cleanup
- [x] Targeted Surefire suite GREEN
- [x] No method bodies modified (import-only changes)

## Self-Check: PASSED

22 unused imports cleaned. Defensive validation invariants confirmed across the entire
codebase. Compile + targeted-test gate green.
