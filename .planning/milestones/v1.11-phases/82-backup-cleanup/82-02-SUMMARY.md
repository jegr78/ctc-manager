---
phase: 82-backup-cleanup
plan: 02
subsystem: infra
tags: [spring-boot, yaml, configuration, profile-isolation, backup]

# Dependency graph
requires:
  - phase: 82-backup-cleanup
    provides: "Phase 82 context, D-12 (IN-04 target expression), research Task 8 (no per-profile overrides)"
provides:
  - "application.yml import-backups-dir uses profile-isolated path data/${spring.profiles.active:dev}/import-backups"
  - "README Backup & Restore section reflects the new profile-isolated recovery path"
affects: [82-backup-cleanup]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Profile-isolated YAML path: data/${spring.profiles.active:dev}/<subdir> (mirrors staging-dir pattern on line 5)"

key-files:
  created: []
  modified:
    - src/main/resources/application.yml
    - README.md

key-decisions:
  - "D-12: Changed import-backups-dir from data/.import-backups to data/${spring.profiles.active:dev}/import-backups — mirrors existing staging-dir pattern on line 5"
  - "D-13: No backward-compat shim; pre-v1.11 artifacts under data/.import-backups/ stay in place; README note added"
  - "D-14: No DB migration, no Flyway change — filesystem-only config change"
  - "D-15: BackupImport* ITs and AutoBackupBeforeImportPathIT verified green (all use @Value or @DynamicPropertySource — not hardcoded paths)"

patterns-established:
  - "All profile-specific runtime dirs follow data/${spring.profiles.active:dev}/<dir> pattern in application.yml"

requirements-completed: [BACK-02]

# Metrics
duration: 15min
completed: 2026-05-16
---

# Phase 82 Plan 02: IN-04 Profile-Isolate import-backups-dir Summary

**Changed application.yml import-backups-dir from shared `data/.import-backups` to profile-isolated `data/${spring.profiles.active:dev}/import-backups`, eliminating cross-profile contamination in 24h recovery storage.**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-05-16T20:45:00Z
- **Completed:** 2026-05-16T20:57:00Z
- **Tasks:** 3 (Task 1: YAML change; Task 2: README update; Task 3: IT verification + atomic commit)
- **Files modified:** 2

## Accomplishments

- Profile-isolated `import-backups-dir` in `application.yml` to mirror the existing `staging-dir` pattern — `data/${spring.profiles.active:dev}/import-backups`
- Updated README "## Backup & Restore" Recovery section: path reference changed from `data/.import-backups/<ts>/` to `data/<profile>/import-backups/<ts>/`; added v1.11 note that pre-v1.11 artifacts under `data/.import-backups/` are not migrated
- All BackupImport* ITs (60 tests total) and AutoBackupBeforeImportPathIT green after change — confirmed via targeted failsafe run

## Task Commits

All three plan tasks were collapsed into one atomic commit per the plan's requirement:

1. **Task 1: Update application.yml** + **Task 2: Update README** + **Task 3: Verify ITs + Commit** - `2dd10775` (fix: IN-04)

**Atomic commit:** `2dd10775` — `fix(82): IN-04 profile-isolate import-backups-dir`

## Files Created/Modified

- `src/main/resources/application.yml` — line 6: `import-backups-dir: data/.import-backups` → `import-backups-dir: data/${spring.profiles.active:dev}/import-backups`
- `README.md` — Recovery section: path reference updated to `data/<profile>/import-backups/<ts>/`; v1.11 note added

## Decisions Made

- No per-profile YAML override needed — confirmed by research Task 8 (grep on all 4 profile YAMLs returned no `import-backups-dir` or `backup` config)
- README change: single sentence + example paths (`data/dev/import-backups/`, `data/prod/import-backups/`) for operator clarity
- No `NEEDS_CONTEXT` abort needed: grep on `src/test/` confirmed no test file hardcodes the old `data/.import-backups` path as an assertion value — all refs are in comments or use `@Value`-injected paths

## Deviations from Plan

None — plan executed exactly as written. The hardcoded path check per Task 1 `<action>` confirmed that `AutoBackupBeforeImportPathIT` and all other ITs use `@Value("${app.backup.import-backups-dir}")` or `@DynamicPropertySource` overrides, not the old hardcoded string. The assertion message on line 144 of `AutoBackupBeforeImportPathIT` says `data/.import-backups/` but this is the assertion *description* string, not a filesystem path assertion — the actual path is resolved from the Spring property.

## Issues Encountered

- Surefire unit test run showed a pre-existing `MatchdaysPageGeneratorTest` Playwright screenshot error (unrelated to this change — a Chromium browser lifecycle issue in the Surefire fork). This did not affect the BackupImport* targeted test verification via Failsafe, which ran clean.

## User Setup Required

None — no external service configuration required.

## Known Stubs

None — both changed files are fully wired. The `application.yml` property is read by `BackupImportService` via `@Value("${app.backup.import-backups-dir}")` and immediately used as a filesystem path root.

## Threat Flags

None — this plan introduces no new network endpoints, auth paths, file access patterns, or schema changes at trust boundaries. The property change is a filesystem path prefix shift; the access pattern (write backup ZIP under a timestamped subdir) is unchanged.

## Next Phase Readiness

- IN-04 fix committed; ready for Plan 82-03 (IN-03: escalate missing ZIP entry to log.warn)
- No blockers

---
*Phase: 82-backup-cleanup*
*Completed: 2026-05-16*
