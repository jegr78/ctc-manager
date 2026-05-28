---
phase: 101-backup-restore-covers-discord-schema-v8-v15
plan: 01
status: complete
commit: 0019c7ed
requirements_addressed:
  - D-09
  - D-14 (SCHEMA_VERSION half only — entity-count half deferred to Plan 02)
---

# Plan 101-01 — SCHEMA_VERSION 1 → 2 atomic flip

## Outcome

Atomic Wave-0 flip of `BackupSchema.SCHEMA_VERSION` from `1` to `2` together with the
paired `BackupSchemaGuardTest` assertion update. Subsequent Phase 101 plans now
inherit a green build to extend from.

## Changes

| File | Change |
|------|--------|
| `src/main/java/org/ctc/backup/schema/BackupSchema.java` | `SCHEMA_VERSION = 1` → `SCHEMA_VERSION = 2` (D-09) |
| `src/test/java/org/ctc/backup/schema/BackupSchemaGuardTest.java` | Test method renamed `...SchemaVersionIsOne` → `...SchemaVersionIsTwo`, assertion `.isEqualTo(1)` → `.isEqualTo(2)`, `.as(...)` message string `"changed from 1"` → `"changed from 2"` |

## Verification

- `./mvnw clean test-compile` — green (no stale IDE cache).
- `./mvnw verify -Dit.test=BackupSchemaGuardTest -DfailIfNoTests=false` — `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` (16.26 s).
- JaCoCo coverage check fails at 72% as expected for a single-test targeted run — phase-end `./mvnw clean verify -Pe2e` is the authoritative gate.
- `grep -rn "SCHEMA_VERSION = 1" src/` — zero matches.
- `grep -rn "SCHEMA_VERSION = 2" src/main/java/org/ctc/backup/schema/BackupSchema.java` — one match.

## Scope Fence Preserved

The following Do-Not-Touch areas remained untouched per plan scope:

- `BackupSchema.initializeExportOrder()` package-filter predicate — Plan 02 owns it.
- `BackupSchemaGuardTest::...ExportOrderHasTwentyFourEntities` — Plan 02 flips entity count 24 → 26 atomically with the package-filter expansion.
- All four existing Restorers (Match, Team, Matchday, Season) — Plan 03 owns them.
- `BackupSerializationModule.java` — Plan 02.
- `BackupImportService.java` schema-version refusal — Plan 04 (lenient `IN (1, 2)`).
- V8-V15 SQL migrations — immutable.

## Commit

```
0019c7ed chore(101): bump BackupSchema.SCHEMA_VERSION 1 → 2 + guard-test flip
```

`git diff --stat` for that commit:

```
 src/main/java/org/ctc/backup/schema/BackupSchema.java          | 2 +-
 src/test/java/org/ctc/backup/schema/BackupSchemaGuardTest.java | 6 +++---
 2 files changed, 4 insertions(+), 4 deletions(-)
```

## Next

Plan 101-02 — atomic 7-change commit: package-filter expansion (`org.ctc.domain.model` → also `org.ctc.discord.model`), `BackupSerializationModule` adds DiscordGlobalConfigMixIn + DiscordPostMixIn, two new Restorer beans, guard-test entity-count flips 24 → 26.
