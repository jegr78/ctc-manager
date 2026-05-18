---
phase: 82-backup-cleanup
plan: 07
completed: 2026-05-17
status: complete
---

# Plan 82-07 — BACK-03 + IN-03 WARN Test

## Outcome

Added a single-ZIP-open instrumentation hook on `BackupImportService` and an integration test that locks two contracts in one class:

- **BACK-03** — `restoreAll` opens the backup ZIP exactly once per `execute(UUID)` call (WR-05 single-pass invariant).
- **IN-03 WARN log** — a backup ZIP missing a data entry emits the WARN corruption-signal message introduced by plan 82-03.

## Changes

**`src/main/java/org/ctc/backup/service/BackupImportService.java`**
- Added `private final AtomicInteger zipOpenCounter` field with inline initializer.
- Added `public int getZipOpenCount()` accessor (required because `@Transactional(readOnly = true)` at class level creates a CGLIB proxy — package-private field access from the test returns null on the proxy).
- Reset counter at top of `execute(UUID stagingId)`.
- Increment counter immediately before `try (ZipFile zf = new ZipFile(...))` in `restoreAll`.

**`src/test/java/org/ctc/backup/service/BackupRestoreZipOpenCountIT.java`** (new)
- `@SpringBootTest @ActiveProfiles("dev") @ExtendWith(OutputCaptureExtension.class) @Tag("integration") @TestInstance(Lifecycle.PER_CLASS)`.
- `@DynamicPropertySource` redirects `app.backup.import-backups-dir` to a per-class temp dir (prevents collision with operator-recovery state under `data/dev/import-backups/`).
- `@BeforeEach` wipes the temp dir between tests (avoids same-second timestamp directory collision between the two `@Test` methods).
- `@BeforeAll` seeds the dev fixture via `TestDataService.seed()`.
- Test 1 `givenStagedBackup_whenExecuteImport_thenZipOpenedExactlyOnce` — exports the dev fixture to bytes, stages, executes, asserts `getZipOpenCount() == 1`.
- Test 2 `givenZipWithMissingEntry_whenRestore_thenWarnLogEmittedAndZeroRows` — strips `data/cars.json` from the exported bytes, stages the truncated archive, executes, asserts the captured output contains the IN-03 WARN message prefix.

## Verification

- `./mvnw clean verify -Dit.test=BackupRestoreZipOpenCountIT -DfailIfNoTests=false -q` — exit 0, 2 tests / 0 errors / 0 failures.
- `./mvnw spotbugs:check -DskipTests` — `BugInstance size is 0`, BUILD SUCCESS.

## Commits

- `53c4131b` test(82): BACK-03 ZipEntry-open count IT + IN-03 missing-entry WARN

## Requirements covered

- BACK-03 — ZIP-open count IT
- IN-03 (test) — WARN-log assertion co-located per CONTEXT.md D-11
